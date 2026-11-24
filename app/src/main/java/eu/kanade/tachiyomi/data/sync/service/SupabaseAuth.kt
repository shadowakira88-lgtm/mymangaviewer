package eu.kanade.tachiyomi.data.sync.service

import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy

// KMK -->
/**
 * Minimal Supabase Auth (GoTrue) client using the REST API: sign up, sign in with password and token refresh.
 */
class SupabaseAuth(private val syncPreferences: SyncPreferences) {

    private val network: NetworkHelper by injectLazy()
    private val json: Json by injectLazy()

    private val baseUrl get() = syncPreferences.supabaseUrl().get().trim().trimEnd('/')
    private val anonKey get() = syncPreferences.supabaseAnonKey().get().trim()

    val isConfigured get() = baseUrl.isNotBlank() && anonKey.isNotBlank()
    val isSignedIn get() = syncPreferences.supabaseRefreshToken().get().isNotBlank()

    fun authHeaders(token: String? = null): Headers = Headers.Builder()
        .add("apikey", anonKey)
        .add("Authorization", "Bearer ${token ?: anonKey}")
        .build()

    suspend fun signIn(email: String, password: String) {
        request(
            "$baseUrl/auth/v1/token?grant_type=password",
            buildJsonObject {
                put("email", email)
                put("password", password)
            },
        )
        syncPreferences.supabaseEmail().set(email)
    }

    /** Returns true if a session was created immediately, false if email confirmation is still required. */
    suspend fun signUp(email: String, password: String): Boolean {
        val obj = request(
            "$baseUrl/auth/v1/signup",
            buildJsonObject {
                put("email", email)
                put("password", password)
            },
        )
        syncPreferences.supabaseEmail().set(email)
        return obj["access_token"]?.jsonPrimitive?.contentOrNull != null
    }

    fun signOut() {
        syncPreferences.supabaseAccessToken().set("")
        syncPreferences.supabaseRefreshToken().set("")
        syncPreferences.supabaseUserId().set("")
    }

    /** Refreshes the session and returns a valid access token. */
    suspend fun freshToken(): String {
        val refresh = syncPreferences.supabaseRefreshToken().get()
        check(refresh.isNotBlank()) { "Not signed in" }
        request(
            "$baseUrl/auth/v1/token?grant_type=refresh_token",
            buildJsonObject { put("refresh_token", refresh) },
        )
        return syncPreferences.supabaseAccessToken().get()
    }

    private suspend fun request(url: String, body: JsonObject): JsonObject = withIOContext {
        check(isConfigured) { "Supabase URL / anon key not set" }
        val req = POST(
            url,
            authHeaders(),
            body.toString().toRequestBody("application/json".toMediaType()),
        )
        network.client.newCall(req).await().use { response ->
            val text = response.body.string()
            val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            if (!response.isSuccessful) {
                val msg = listOf("msg", "error_description", "message")
                    .firstNotNullOfOrNull { (obj?.get(it) as? JsonPrimitive)?.contentOrNull }
                    ?: "HTTP ${response.code}"
                throw IllegalStateException(msg)
            }
            checkNotNull(obj) { "Bad response" }
            (obj["access_token"] as? JsonPrimitive)?.contentOrNull
                ?.let { syncPreferences.supabaseAccessToken().set(it) }
            (obj["refresh_token"] as? JsonPrimitive)?.contentOrNull
                ?.let { syncPreferences.supabaseRefreshToken().set(it) }
            (obj["user"] as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull
                ?.let { syncPreferences.supabaseUserId().set(it) }
            obj
        }
    }
}
// KMK <--
