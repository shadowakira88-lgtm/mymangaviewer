package eu.kanade.tachiyomi.data.sync

import android.content.Context
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.presentation.kodi.KodiWallpaper
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.sync.service.SupabaseAuth
import eu.kanade.tachiyomi.data.sync.service.SupabaseSyncService
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import logcat.logcat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.manga.MangaMapper.mapManga
import tachiyomi.domain.category.interactor.CreateCategoryWithName
import tachiyomi.domain.category.interactor.DeleteCategory
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.ReorderCategory
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.security.MessageDigest

// KMK -->
/**
 * Incremental Supabase sync. Every piece of data is its own small object under `<user id>/delta/`:
 *  - `m_<key>.bin`  one manga (chapters, read progress, bookmarks, per-manga settings)
 *  - `cats.json`    groups and which manga is in which group
 *  - `prefs.bin`    app settings, source settings, extension repos, saved searches, feeds
 *  - `wp_global`, `wp_<key>`  wallpapers
 * Downloads are never synced. A local index remembers what was last pushed / seen, so [pull] (once when the app
 * opens) only fetches objects that changed on the other phone and [push] only uploads what changed here.
 */
class SupabaseDeltaSync(
    private val context: Context,
    private val syncManager: SyncManager,
) {
    private val syncPreferences: SyncPreferences = Injekt.get()
    private val network: NetworkHelper = Injekt.get()
    private val protoBuf: ProtoBuf = Injekt.get()
    private val handler: DatabaseHandler = Injekt.get()
    private val auth = SupabaseAuth(syncPreferences)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val backupCreator = BackupCreator(context, false)

    private val indexFile = File(context.filesDir, "supabase_delta_index.json")
    private var index = loadIndex()
    private var keyToManga: Map<String, Manga>? = null

    @Serializable
    private class Index(
        val local: MutableMap<String, String> = mutableMapOf(),
        val remote: MutableMap<String, String> = mutableMapOf(),
    )

    @Serializable
    private class CatEntry(val name: String, val order: Long, val flags: Long, val hidden: Boolean)

    @Serializable
    private class CatsDoc(val categories: List<CatEntry>, val members: Map<String, List<String>>)

    private val backupOptions
        get() = BackupOptions(
            libraryEntries = true,
            categories = false,
            chapters = true,
            tracking = true,
            history = true,
            extensionStores = true,
            appSettings = true,
            sourceSettings = true,
            privateSettings = false,
            customInfo = true,
            readEntries = true,
            savedSearchesFeeds = true,
        )

    suspend fun run(pull: Boolean) {
        check(auth.isConfigured && auth.isSignedIn) { "Supabase account not signed in" }
        val token = auth.freshToken()
        if (pull) pull(token)
        push(token)
        syncPreferences.lastSyncTimestamp().set(System.currentTimeMillis())
    }

    // region pull

    private suspend fun pull(token: String) {
        val listed = list(token)
        val changed = listed.filter { (name, stamp) -> index.remote[name] != stamp }
        if (changed.isEmpty()) return
        logcat(LogPriority.DEBUG) { "Delta pull: ${changed.size} changed objects" }

        // 1. manga
        val remoteManga = mutableListOf<BackupManga>()
        val sources = mutableMapOf<Long, BackupSource>()
        val mangaNames = changed.keys.filter { it.startsWith("m_") }
        mangaNames.forEach { name ->
            val bytes = download(token, name) ?: return@forEach
            runCatching { protoBuf.decodeFromByteArray(Backup.serializer(), bytes) }.getOrNull()?.let { b ->
                remoteManga += b.backupManga
                b.backupSources.forEach { sources[it.sourceId] = it }
            }
        }
        if (remoteManga.isNotEmpty()) {
            syncManager.applyRemoteManga(Backup(backupManga = remoteManga, backupSources = sources.values.toList()))
            keyToManga = null
            remoteManga.forEach { m ->
                val name = "m_${key(m.source, m.url)}"
                currentMangaFingerprint(m.source, m.url)?.let { index.local[name] = it }
            }
        }

        // 2. settings (remote wins only when nothing changed here since the last sync)
        if (PREFS in changed) {
            val bytes = download(token, PREFS)
            if (bytes != null && localUnchanged(PREFS, prefsBytes())) {
                runCatching { protoBuf.decodeFromByteArray(Backup.serializer(), bytes) }.getOrNull()?.let {
                    syncManager.applyRemotePrefs(it)
                    index.local[PREFS] = hash(prefsBytes())
                }
            }
        }

        // 3. groups and their members
        if (CATS in changed) {
            val bytes = download(token, CATS)
            if (bytes != null && localUnchanged(CATS, catsJson().toByteArray())) {
                runCatching { json.decodeFromString(CatsDoc.serializer(), bytes.decodeToString()) }.getOrNull()
                    ?.let {
                        applyCats(it)
                        index.local[CATS] = hash(catsJson().toByteArray())
                    }
            }
        }

        // 4. wallpapers
        changed.keys.filter { it.startsWith("wp_") }.forEach { name ->
            val target = wallpaperFile(name) ?: return@forEach
            if (target.exists() && index.local[name] != wallpaperFingerprint(target)) return@forEach
            val bytes = download(token, name) ?: return@forEach
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            index.local[name] = wallpaperFingerprint(target)
            KodiWallpaper.notifyChanged()
        }

        index.remote.putAll(listed)
        saveIndex()
    }

    private fun localUnchanged(name: String, current: ByteArray): Boolean {
        val last = index.local[name] ?: return true
        return last == hash(current)
    }

    private suspend fun applyCats(doc: CatsDoc) {
        val getCategories = Injekt.get<GetCategories>()
        val create = Injekt.get<CreateCategoryWithName>()
        val delete = Injekt.get<DeleteCategory>()
        val reorder = Injekt.get<ReorderCategory>()
        val names = doc.categories.map { it.name }.toSet()
        var local = getCategories.await().filter { !it.isSystemCategory }
        local.filter { it.name !in names }.forEach { delete.await(it.id) }
        doc.categories.forEach { c ->
            if (local.none { it.name == c.name }) create.await(c.name)
        }
        local = getCategories.await().filter { !it.isSystemCategory }
        doc.categories.sortedBy { it.order }.forEachIndexed { i, c ->
            local.firstOrNull { it.name == c.name }?.let { cat -> reorder.await(cat, i) }
        }
        val byName = getCategories.await().associateBy { it.name }
        val setCategories = Injekt.get<SetMangaCategories>()
        val manga = mangaByKey()
        doc.members.forEach { (key, groupNames) ->
            val m = manga[key] ?: return@forEach
            setCategories.await(m.id, groupNames.mapNotNull { byName[it]?.id })
        }
    }

    // endregion

    // region push

    private suspend fun push(token: String) {
        val pushed = mutableListOf<String>()

        // manga: only the ones whose version changed since the last push
        val all = handler.awaitList { mangasQueries.getMangasWithFavoriteTimestamp(::mapManga) }
        val dirty = all.filter { index.local["m_${key(it.source, it.url)}"] != fingerprint(it) }
        dirty.chunked(20).forEach { chunk ->
            val backupManga = backupCreator.backupMangas(chunk, backupOptions)
            backupManga.forEach { bm ->
                val single = Backup(
                    backupManga = listOf(bm.also { it.categories = emptyList() }),
                    backupSources = backupCreator.backupSources(listOf(bm)),
                )
                val name = "m_${key(bm.source, bm.url)}"
                val fp = chunk.firstOrNull { it.source == bm.source && it.url == bm.url }?.let(::fingerprint)
                if (upload(token, name, protoBuf.encodeToByteArray(Backup.serializer(), single)) && fp != null) {
                    index.local[name] = fp
                    pushed += name
                }
            }
        }

        // settings
        prefsBytes().let { bytes ->
            if (index.local[PREFS] != hash(bytes) && upload(token, PREFS, bytes)) {
                index.local[PREFS] = hash(bytes)
                pushed += PREFS
            }
        }

        // groups
        catsJson().toByteArray().let { bytes ->
            if (index.local[CATS] != hash(bytes) && upload(token, CATS, bytes)) {
                index.local[CATS] = hash(bytes)
                pushed += CATS
            }
        }

        // wallpapers
        KodiWallpaper.file(context).takeIf { it.exists() }?.let { f ->
            pushWallpaper(token, "wp_global", f, pushed)
        }
        val wallpaperDir = File(context.filesDir, "manga_wallpapers")
        if (wallpaperDir.isDirectory) {
            val manga = all.associateBy { it.id }
            wallpaperDir.listFiles()?.forEach { f ->
                val m = f.name.toLongOrNull()?.let(manga::get) ?: return@forEach
                pushWallpaper(token, "wp_${key(m.source, m.url)}", f, pushed)
            }
        }

        if (pushed.isNotEmpty()) {
            // remember the new stamps so our own uploads are not downloaded again on the next open
            list(token).let { stamps -> pushed.forEach { n -> stamps[n]?.let { index.remote[n] = it } } }
        }
        saveIndex()
    }

    private suspend fun pushWallpaper(token: String, name: String, file: File, pushed: MutableList<String>) {
        val fp = wallpaperFingerprint(file)
        if (index.local[name] == fp) return
        if (upload(token, name, file.readBytes())) {
            index.local[name] = fp
            pushed += name
        }
    }

    private suspend fun prefsBytes(): ByteArray {
        val o = backupOptions
        val b = Backup(
            backupManga = emptyList(),
            backupPreferences = backupCreator.backupAppPreferences(o),
            backupSourcePreferences = backupCreator.backupSourcePreferences(o),
            backupExtensionStores = backupCreator.backupExtensionStores(o),
            backupSavedSearches = backupCreator.backupSavedSearches(o),
            backupFeeds = backupCreator.backupFeeds(o),
        )
        return protoBuf.encodeToByteArray(Backup.serializer(), b)
    }

    private suspend fun catsJson(): String {
        val cats = Injekt.get<GetCategories>().await().filter { !it.isSystemCategory }
        val names = cats.associate { it.id to it.name }
        val members = Injekt.get<GetLibraryManga>().await()
            .filter { it.categories.isNotEmpty() }
            .associate { lm ->
                key(lm.manga.source, lm.manga.url) to lm.categories.mapNotNull { names[it] }.sorted()
            }
            .toSortedMap()
        val doc = CatsDoc(
            cats.sortedBy { it.order }.map { CatEntry(it.name, it.order, it.flags, it.hidden) },
            members,
        )
        return json.encodeToString(CatsDoc.serializer(), doc)
    }

    // endregion

    // region helpers

    private suspend fun mangaByKey(): Map<String, Manga> {
        keyToManga?.let { return it }
        val all = handler.awaitList { mangasQueries.getAllManga(::mapManga) }
        return all.associateBy { key(it.source, it.url) }.also { keyToManga = it }
    }

    private suspend fun currentMangaFingerprint(source: Long, url: String): String? =
        mangaByKey()[key(source, url)]?.let(::fingerprint)

    private fun wallpaperFile(name: String): File? {
        if (name == "wp_global") return KodiWallpaper.file(context)
        val key = name.removePrefix("wp_")
        val manga = keyToManga?.get(key) ?: return null
        return KodiWallpaper.mangaFile(context, manga.id)
    }

    private fun fingerprint(m: Manga) = "${m.version}:${m.lastModifiedAt}:${m.favorite}"

    private fun wallpaperFingerprint(f: File) = "${f.lastModified()}:${f.length()}"

    private fun key(source: Long, url: String) = hash("$source|$url".toByteArray()).take(24)

    private fun hash(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun loadIndex(): Index = runCatching {
        json.decodeFromString(Index.serializer(), indexFile.readText())
    }.getOrDefault(Index())

    private fun saveIndex() {
        runCatching { indexFile.writeText(json.encodeToString(Index.serializer(), index)) }
    }

    // endregion

    // region storage api

    private val base get() = syncPreferences.supabaseUrl().get().trim().trimEnd('/')
    private val uid get() = syncPreferences.supabaseUserId().get()
    private val bucket = SupabaseSyncService.BUCKET

    /** name -> updated_at of every object in the user's delta folder. */
    private suspend fun list(token: String): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()
        var offset = 0
        while (true) {
            val body = buildJsonObject {
                put("prefix", "$uid/delta")
                put("limit", PAGE)
                put("offset", offset)
            }.toString().toRequestBody("application/json".toMediaType())
            val req = POST("$base/storage/v1/object/list/$bucket", auth.authHeaders(token), body)
            val page = network.client.newCall(req).await().use { r ->
                if (!r.isSuccessful) throw IllegalStateException("List failed: ${r.code}")
                json.parseToJsonElement(r.body.string()) as? JsonArray ?: JsonArray(emptyList())
            }
            page.forEach { el ->
                val o: JsonObject = el.jsonObject
                val name = o["name"]?.jsonPrimitive?.content ?: return@forEach
                result[name] = o["updated_at"]?.jsonPrimitive?.content.orEmpty()
            }
            if (page.size < PAGE) break
            offset += PAGE
        }
        return result
    }

    private suspend fun download(token: String, name: String): ByteArray? {
        val url = "$base/storage/v1/object/authenticated/$bucket/$uid/delta/$name"
        return network.client.newCall(GET(url, auth.authHeaders(token))).await().use { r ->
            if (r.isSuccessful) r.body.bytes() else null
        }
    }

    private suspend fun upload(token: String, name: String, bytes: ByteArray): Boolean {
        val headers = auth.authHeaders(token).newBuilder().add("x-upsert", "true").build()
        val req = POST(
            "$base/storage/v1/object/$bucket/$uid/delta/$name",
            headers,
            bytes.toRequestBody("application/octet-stream".toMediaType()),
        )
        return network.client.newCall(req).await().use { r ->
            if (!r.isSuccessful) logcat(LogPriority.ERROR) { "Delta upload $name failed: ${r.code}" }
            r.isSuccessful
        }
    }

    // endregion

    private companion object {
        const val PREFS = "prefs.bin"
        const val CATS = "cats.json"
        const val PAGE = 1000
    }
}
// KMK <--
