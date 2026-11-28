package eu.kanade.tachiyomi.data.sync

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.presentation.kodi.KodiWallpaper
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.sync.service.SupabaseAuth
import eu.kanade.tachiyomi.data.sync.service.SupabaseSyncService
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.manga.MangaMapper.mapManga
import tachiyomi.domain.category.interactor.CreateCategoryWithName
import tachiyomi.domain.category.interactor.DeleteCategory
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.ReorderCategory
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
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
        /** True once a complete pull has finished; nothing is ever pushed before that. */
        var pulled: Boolean = false,
        val local: MutableMap<String, String> = mutableMapOf(),
        val remote: MutableMap<String, String> = mutableMapOf(),
        /** Extension list last known on the server, so a push never shrinks it. */
        var extDoc: String = "",
    )

    @Serializable
    private class ExtEntry(val pkg: String, val sig: String, val name: String, val sources: List<Long>)

    @Serializable
    private class ExtDoc(val extensions: List<ExtEntry>)

    @Serializable
    private class ChProg(val read: Boolean, val bookmark: Boolean, val page: Long, val mod: Long)

    @Serializable
    private class HistEntry(val url: String, val lastRead: Long, val dur: Long)

    @Serializable
    private class ProgressDoc(val chapters: Map<String, ChProg>, val history: List<HistEntry>)

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

    var downloaded = 0
        private set
    var uploaded = 0
        private set

    private suspend fun report(text: String, done: Int, total: Int) {
        if (total > 20 && done % 5 != 0 && done != total) return
        syncManager.reportProgress(text, done, total)
    }

    private var authoritative = false

    /**
     * @param mode [MODE_NORMAL], [MODE_UPLOAD] (the server becomes a copy of this phone) or [MODE_DOWNLOAD] (this
     * phone becomes a copy of the server).
     */
    suspend fun run(pull: Boolean, mode: Int = MODE_NORMAL) {
        check(auth.isConfigured && auth.isSignedIn) { "Supabase account not signed in" }
        val token = auth.freshToken()
        when (mode) {
            MODE_DOWNLOAD -> {
                index = Index()
                authoritative = true
                pull(token)
                index.pulled = true
                saveIndex()
                syncPreferences.lastSyncTimestamp().set(System.currentTimeMillis())
                return
            }
            MODE_CLEAR_LIBRARY -> {
                clearLibrary(token)
                return
            }
            MODE_UPLOAD -> {
                index = Index(pulled = true)
                saveIndex()
            }
            else -> {
                // A phone that never finished a pull must not push anything (it would wipe the server).
                if (pull || !index.pulled) {
                    pull(token)
                    index.pulled = true
                    saveIndex()
                }
            }
        }
        push(token)
        syncPreferences.lastSyncTimestamp().set(System.currentTimeMillis())
    }

    /** Deletes the library data on the server (manga, read progress, groups). Settings, extensions and wallpapers stay. */
    private suspend fun clearLibrary(token: String) {
        val names = list(token).keys.filter { it.startsWith("m_") || it.startsWith("p_") || it == CATS }
        names.chunked(100).forEachIndexed { i, chunk ->
            val body = buildJsonObject {
                put(
                    "prefixes",
                    JsonArray(chunk.map { kotlinx.serialization.json.JsonPrimitive("$uid/delta/$it") }),
                )
            }.toString().toRequestBody("application/json".toMediaType())
            val req = okhttp3.Request.Builder()
                .url("$base/storage/v1/object/$bucket")
                .headers(auth.authHeaders(token))
                .delete(body)
                .build()
            network.client.newCall(req).await().use { r ->
                check(r.isSuccessful) { "Delete failed: ${r.code}" }
            }
            report("Clear server", minOf((i + 1) * 100, names.size), names.size)
        }
        // forget them locally too, so the next upload sends everything again
        index.local.keys.removeAll { it.startsWith("m_") || it.startsWith("p_") || it.startsWith("s_") || it == CATS }
        index.remote.keys.removeAll { it.startsWith("m_") || it.startsWith("p_") || it == CATS }
        saveIndex()
        downloaded = 0
        uploaded = names.size
    }

    // region pull

    private suspend fun phase(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The stamp of a failed part is not recorded, so it is retried next time; the other parts continue.
            logcat(LogPriority.ERROR, throwable = e) { "Delta pull part $name failed" }
        }
    }

    private suspend fun catsLocalUnchanged(): Boolean {
        if (authoritative) return true
        val last = index.local[CATS]
        if (last == null) {
            // never synced: the server wins only on a phone that has no groups of its own yet
            return Injekt.get<GetCategories>().await().none { !it.isSystemCategory }
        }
        return last == hash(catsJson().toByteArray())
    }

    private suspend fun pull(token: String) {
        val listed = list(token)
        // Compare with what was already applied: only objects that are new or changed on the server are downloaded.
        val changed = listed.filter { (name, stamp) -> index.remote[name] != stamp }
        if (changed.isEmpty()) return
        logcat(LogPriority.DEBUG) { "Delta pull: ${changed.size} changed objects" }

        // 1. Elenaku / browse settings, source settings, extension repos, saved searches, feeds
        if (PREFS in changed) {
            report("Setting", 0, 1)
            val bytes = download(token, PREFS)
            if (bytes != null && localUnchanged(PREFS, prefsBytes())) {
                runCatching { protoBuf.decodeFromByteArray(Backup.serializer(), bytes) }.getOrNull()?.let {
                    syncManager.applyRemotePrefs(it)
                    index.local[PREFS] = hash(prefsBytes())
                    downloaded++
                }
            }
            index.remote[PREFS] = changed.getValue(PREFS)
            saveIndex()
        }

        // 2. extensions (the important part: sources only work once their extension is installed)
        if (EXT in changed) {
            phase("extensions") {
                val bytes = download(token, EXT)
                if (bytes != null) {
                    val text = bytes.decodeToString()
                    runCatching { json.decodeFromString(ExtDoc.serializer(), text) }.getOrNull()?.let {
                        installMissingExtensions(it)
                        index.extDoc = text
                    }
                }
                index.remote[EXT] = changed.getValue(EXT)
                saveIndex()
            }
        }

        // 3. groups
        var catsDoc: CatsDoc? = null
        if (CATS in changed) {
            phase("groups") {
                report("Group", 0, 1)
                val bytes = download(token, CATS) ?: return@phase
                val doc = runCatching { json.decodeFromString(CatsDoc.serializer(), bytes.decodeToString()) }
                    .getOrNull() ?: return@phase
                if (catsLocalUnchanged()) {
                    applyGroups(doc, bytes)
                    catsDoc = doc
                    downloaded++
                }
                index.remote[CATS] = changed.getValue(CATS)
                saveIndex()
            }
        }

        // 4. manga, group by group (members of the first group first), then the ones without a group
        val doc = catsDoc
        val members = doc?.members ?: emptyMap()
        val order = doc?.categories?.sortedBy { it.order }?.map { it.name } ?: emptyList()
        val mangaNames = changed.keys.filter { it.startsWith("m_") }
            .sortedBy { name ->
                val groups = members[name.removePrefix("m_")]
                if (groups.isNullOrEmpty()) {
                    Int.MAX_VALUE
                } else {
                    groups.minOf { g -> order.indexOf(g).let { if (it >= 0) it else 9999 } }
                }
            }
        val categoryIds = Injekt.get<GetCategories>().await().associate { it.name to it.id }
        mangaNames.chunked(10).forEachIndexed { batchIndex, batch ->
            val remoteManga = mutableListOf<BackupManga>()
            val sources = mutableMapOf<Long, BackupSource>()
            batch.forEach { name ->
                val bytes = download(token, name) ?: return@forEach
                runCatching { protoBuf.decodeFromByteArray(Backup.serializer(), bytes) }.getOrNull()?.let { b ->
                    remoteManga += b.backupManga
                    b.backupSources.forEach { sources[it.sourceId] = it }
                }
            }
            if (remoteManga.isNotEmpty()) {
                syncManager.applyRemoteManga(
                    Backup(backupManga = remoteManga, backupSources = sources.values.toList()),
                )
                val setCategories = Injekt.get<SetMangaCategories>()
                val mangaRepository = Injekt.get<MangaRepository>()
                remoteManga.forEach { m ->
                    val k = key(m.source, m.url)
                    val local = mangaRepository.getMangaByUrlAndSourceId(m.url, m.source) ?: return@forEach
                    members[k]?.let { names ->
                        setCategories.await(local.id, names.mapNotNull { categoryIds[it] })
                    }
                    index.local["m_$k"] = fingerprint(local)
                    index.local["s_$k"] = structureHash(m)
                    downloaded++
                }
            }
            batch.forEach { index.remote[it] = changed.getValue(it) }
            saveIndex()
            report("Manga", minOf((batchIndex + 1) * 10, mangaNames.size), mangaNames.size)
        }

        // 5. read progress / history of manga whose full object did not change (a small file per manga)
        keyToManga = null
        val progressNames = changed.keys.filter {
            it.startsWith("p_") && "m_" + it.removePrefix("p_").removeSuffix(".json") !in mangaNames
        }
        if (progressNames.isNotEmpty()) {
            val byKey = mangaByKey()
            progressNames.forEachIndexed { i, name ->
                val k = name.removePrefix("p_").removeSuffix(".json")
                val local = byKey[k]
                val text = download(token, name)?.decodeToString()
                if (local != null && text != null) {
                    runCatching { json.decodeFromString(ProgressDoc.serializer(), text) }.getOrNull()?.let {
                        applyProgress(local, it)
                        Injekt.get<MangaRepository>().getMangaByUrlAndSourceId(local.url, local.source)?.let { m ->
                            index.local["m_$k"] = fingerprint(m)
                        }
                        index.local[name] = hash(text.toByteArray())
                        downloaded++
                    }
                }
                index.remote[name] = changed.getValue(name)
                report("Read / history", i + 1, progressNames.size)
            }
            saveIndex()
        }

        // 6. wallpapers
        val wallpapers = changed.keys.filter { it.startsWith("wp_") }
        if (wallpapers.isNotEmpty()) mangaByKey()
        wallpapers.forEachIndexed { i, name ->
            val target = wallpaperFile(name)
            if (target != null && !(target.exists() && index.local[name] != wallpaperFingerprint(target))) {
                download(token, name)?.let { bytes ->
                    target.parentFile?.mkdirs()
                    target.writeBytes(bytes)
                    index.local[name] = wallpaperFingerprint(target)
                    KodiWallpaper.notifyChanged()
                    downloaded++
                }
            }
            index.remote[name] = changed.getValue(name)
            report("Wallpaper", i + 1, wallpapers.size)
        }

        // anything else (e.g. objects of an older version) is just remembered
        index.remote.putAll(listed.filter { (n, _) -> n !in index.remote })
        saveIndex()
    }

    private suspend fun installMissingExtensions(doc: ExtDoc) {
        val manager = Injekt.get<ExtensionManager>()
        val installed = manager.installedExtensionsFlow.value.map { it.pkgName }.toSet()
        val blank = Injekt.get<SourcePreferences>().disabledSources().get()
        val missing = doc.extensions.filter { e ->
            e.pkg !in installed && !(e.sources.isNotEmpty() && e.sources.all { it.toString() in blank })
        }
        if (missing.isEmpty()) return
        runCatching { manager.findAvailableExtensions() }
        val available = manager.availableExtensionsFlow.first()
        val basePrefs = Injekt.get<BasePreferences>().extensionInstaller()
        if (basePrefs.get() != BasePreferences.ExtensionInstaller.SHIZUKU) {
            basePrefs.set(BasePreferences.ExtensionInstaller.PRIVATE)
        }
        missing.forEachIndexed { i, e ->
            report("Extension: ${e.name}", i, missing.size)
            val ext = available.firstOrNull { it.pkgName == e.pkg && it.signatureHash == e.sig }
                ?: available.firstOrNull { it.pkgName == e.pkg }
                ?: return@forEachIndexed
            runCatching {
                withTimeout(180_000) {
                    manager.installExtension(ext).first { it.isCompleted() }
                }
            }
            downloaded++
        }
        report("Extension", missing.size, missing.size)
    }

    private fun localUnchanged(name: String, current: ByteArray): Boolean {
        val last = index.local[name] ?: return true
        return last == hash(current)
    }

    /**
     * Makes the local groups match the server: create missing ones, set members, order, and only then delete the
     * local groups that are gone from the server AND are empty now (so a bad server copy can never wipe a group
     * that still holds manga).
     */
    private suspend fun applyGroups(doc: CatsDoc, remoteBytes: ByteArray) {
        val getCategories = Injekt.get<GetCategories>()
        val create = Injekt.get<CreateCategoryWithName>()
        val delete = Injekt.get<DeleteCategory>()
        val reorder = Injekt.get<ReorderCategory>()
        val names = doc.categories.map { it.name }.toSet()
        var local = getCategories.await().filter { !it.isSystemCategory }
        doc.categories.forEach { c ->
            if (local.none { it.name == c.name }) create.await(c.name)
        }
        val byName = getCategories.await().associateBy { it.name }
        val setCategories = Injekt.get<SetMangaCategories>()
        val manga = mangaByKey()
        doc.members.forEach { (key, groupNames) ->
            val m = manga[key] ?: return@forEach
            setCategories.await(m.id, groupNames.mapNotNull { byName[it]?.id })
        }
        val libraryCategories = Injekt.get<GetLibraryManga>().await().flatMap { it.categories }.toSet()
        local = getCategories.await().filter { !it.isSystemCategory }
        local.filter { it.name !in names && (authoritative || it.id !in libraryCategories) }
            .forEach { delete.await(it.id, removeManga = false) }
        if (authoritative) {
            // this phone follows the server: manga that are in no group on the server leave all groups here
            val withGroups = doc.members.keys
            manga.filterKeys { it !in withGroups }.values.forEach { m ->
                setCategories.await(m.id, emptyList())
            }
        }
        local = getCategories.await().filter { !it.isSystemCategory }
        doc.categories.sortedBy { it.order }.forEachIndexed { i, c ->
            local.firstOrNull { it.name == c.name }?.let { cat -> reorder.await(cat, i) }
        }
        // remember the server copy: if the local result differs from it, the next push repairs the server
        index.local[CATS] = hash(remoteBytes)
    }

    private suspend fun applyProgress(manga: Manga, doc: ProgressDoc) {
        val chapters = Injekt.get<GetChaptersByMangaId>().await(manga.id)
        val updates = chapters.mapNotNull { ch ->
            val r = doc.chapters[ch.url] ?: return@mapNotNull null
            val newer = r.mod > ch.lastModifiedAt || (r.read && !ch.read)
            if (newer && (r.read != ch.read || r.bookmark != ch.bookmark || r.page != ch.lastPageRead)) {
                ChapterUpdate(
                    id = ch.id,
                    read = r.read || ch.read,
                    bookmark = r.bookmark,
                    lastPageRead = if (r.read) 0L else r.page,
                )
            } else {
                null
            }
        }
        if (updates.isNotEmpty()) Injekt.get<UpdateChapter>().awaitAll(updates)
        val historyRepository = Injekt.get<HistoryRepository>()
        val localHistory = historyRepository.getHistoryByMangaId(manga.id).associateBy { it.chapterId }
        val byUrl = chapters.associateBy { it.url }
        val historyUpdates = doc.history.mapNotNull { h ->
            val ch = byUrl[h.url] ?: return@mapNotNull null
            val local = localHistory[ch.id]
            val localAt = local?.readAt?.time ?: 0L
            if (h.lastRead > localAt) {
                HistoryUpdate(ch.id, Date(h.lastRead), maxOf(0L, h.dur - (local?.readDuration ?: 0L)))
            } else {
                null
            }
        }
        if (historyUpdates.isNotEmpty()) historyRepository.upsertHistory(historyUpdates)
    }

    // endregion

    // region push

    private suspend fun push(token: String) {
        val pushed = mutableListOf<String>()

        // manga: only the ones whose version changed since the last push
        val all = handler.awaitList { mangasQueries.getMangasWithFavoriteTimestamp(::mapManga) }
        val dirty = all.filter { index.local["m_${key(it.source, it.url)}"] != fingerprint(it) }
        dirty.chunked(20).forEachIndexed { chunkIndex, chunk ->
            val backupManga = backupCreator.backupMangas(chunk, backupOptions)
            backupManga.forEach { bm ->
                val k = key(bm.source, bm.url)
                val fp = chunk.firstOrNull { it.source == bm.source && it.url == bm.url }?.let(::fingerprint)
                    ?: return@forEach
                bm.categories = emptyList()
                val progressText = json.encodeToString(ProgressDoc.serializer(), progressOf(bm))
                val full = Backup(
                    backupManga = listOf(bm),
                    backupSources = backupCreator.backupSources(listOf(bm)),
                )
                val fullBytes = protoBuf.encodeToByteArray(Backup.serializer(), full)
                val struct = structureHash(bm)
                var ok = true
                if (index.local["s_$k"] != struct) {
                    // the manga itself changed (new chapters, settings, ...): upload the full object
                    ok = upload(token, "m_$k", fullBytes)
                    if (ok) {
                        index.local["s_$k"] = struct
                        pushed += "m_$k"
                    }
                }
                // read / bookmark / history changes only touch this small file
                if (ok) {
                    val pName = "p_$k.json"
                    val pHash = hash(progressText.toByteArray())
                    if (index.local[pName] != pHash) {
                        ok = upload(token, pName, progressText.toByteArray())
                        if (ok) {
                            index.local[pName] = pHash
                            pushed += pName
                        }
                    }
                }
                if (ok) {
                    index.local["m_$k"] = fp
                    uploaded++
                }
            }
            saveIndex()
            report("Upload manga", minOf((chunkIndex + 1) * 20, dirty.size), dirty.size)
        }

        // extensions: union with what the server already has, so an unfinished install never shrinks the list
        val manager = Injekt.get<ExtensionManager>()
        val local = manager.installedExtensionsFlow.value.map {
            ExtEntry(it.pkgName, it.signatureHash, it.name, it.sources.map { s -> s.id })
        }
        val remoteList = runCatching { json.decodeFromString(ExtDoc.serializer(), index.extDoc).extensions }
            .getOrDefault(emptyList())
        val blank = Injekt.get<SourcePreferences>().disabledSources().get()
        val merged = (remoteList + local).associateBy { it.pkg }.values
            .filterNot { e -> e.sources.isNotEmpty() && e.sources.all { it.toString() in blank } }
            .sortedBy { it.pkg }
        val extText = json.encodeToString(ExtDoc.serializer(), ExtDoc(merged))
        if (merged.isNotEmpty() && index.local[EXT] != hash(extText.toByteArray())) {
            if (upload(token, EXT, extText.toByteArray())) {
                index.local[EXT] = hash(extText.toByteArray())
                index.extDoc = extText
                pushed += EXT
                uploaded++
            }
        }

        // settings
        prefsBytes().let { bytes ->
            if (index.local[PREFS] != hash(bytes) && upload(token, PREFS, bytes)) {
                index.local[PREFS] = hash(bytes)
                pushed += PREFS
                uploaded++
            }
        }

        // groups
        catsJson().toByteArray().let { bytes ->
            if (index.local[CATS] != hash(bytes) && upload(token, CATS, bytes)) {
                index.local[CATS] = hash(bytes)
                pushed += CATS
                uploaded++
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
            uploaded++
        }
    }

    private fun progressOf(bm: BackupManga) = ProgressDoc(
        bm.chapters.filter { it.read || it.bookmark || it.lastPageRead > 0 }
            .associate { it.url to ChProg(it.read, it.bookmark, it.lastPageRead, it.lastModifiedAt) },
        bm.history.map { HistEntry(it.url, it.lastRead, it.readDuration) },
    )

    /** Hash of everything about a manga except read progress, history and version counters. */
    private fun structureHash(bm: BackupManga): String {
        val chapters = bm.chapters.map { ch ->
            BackupChapter(
                url = ch.url,
                name = ch.name,
                scanlator = ch.scanlator,
                dateUpload = ch.dateUpload,
                chapterNumber = ch.chapterNumber,
                sourceOrder = ch.sourceOrder,
            )
        }
        val savedChapters = bm.chapters
        val savedHistory = bm.history
        val savedVersion = bm.version
        val savedModified = bm.lastModifiedAt
        bm.chapters = chapters
        bm.history = emptyList()
        bm.version = 0
        bm.lastModifiedAt = 0
        val bytes = protoBuf.encodeToByteArray(
            Backup.serializer(),
            Backup(backupManga = listOf(bm)),
        )
        bm.chapters = savedChapters
        bm.history = savedHistory
        bm.version = savedVersion
        bm.lastModifiedAt = savedModified
        return hash(bytes)
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
        const val EXT = "ext.json"
        const val MODE_NORMAL = 0
        const val MODE_UPLOAD = 1
        const val MODE_DOWNLOAD = 2
        const val MODE_CLEAR_LIBRARY = 3
        const val PAGE = 1000
    }
}
// KMK <--
