package id.desa.presensi.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

private val Context.sessionStore by preferencesDataStore(name = "presensi_session")

@Serializable
data class Session(
    val userId: String,
    val nama: String,
    val role: String,
    val nipd: String,
    val jabatan: String? = null,
    val villageId: String? = null,
    val deviceId: String? = null,
    val deviceLimit: Int = 1,
    val accessToken: String? = null,
    val refreshToken: String? = null,
)

/**
 * Sesi disimpan di DataStore. Device ID disimpan di kunci terpisah supaya
 * logout tidak memaksa aktivasi ulang — kunci Keystore tetap ada di HP.
 */
class SessionStore(private val context: Context) {

    @Volatile
    private var cache: Session? = null

    private object Keys {
        val data = stringPreferencesKey("session_json")
        val deviceId = stringPreferencesKey("device_id")
    }

    suspend fun current(): Session? {
        cache?.let { return it }
        val prefs = context.sessionStore.data.first()
        val raw = prefs[Keys.data] ?: return null
        val parsed = runCatching { Json.decodeFromString<Session>(raw) }.getOrNull() ?: return null
        val boundId = prefs[Keys.deviceId]
        val merged = if (parsed.deviceId == null && boundId != null) parsed.copy(deviceId = boundId) else parsed
        cache = merged
        return merged
    }

    fun cached(): Session? = cache

    suspend fun deviceId(): String? = current()?.deviceId ?: context.sessionStore.data.first()[Keys.deviceId]

    suspend fun save(session: Session) {
        cache = session
        context.sessionStore.edit { prefs ->
            prefs[Keys.data] = Json.encodeToString(session)
            session.deviceId?.let { prefs[Keys.deviceId] = it }
        }
    }

    suspend fun updateTokens(access: String, refresh: String) {
        val existing = current() ?: return
        save(existing.copy(accessToken = access, refreshToken = refresh))
    }

    /** Logout: token dibuang, deviceId dipertahankan. */
    suspend fun clear() {
        cache = null
        context.sessionStore.edit { prefs -> prefs.remove(Keys.data) }
    }

    /** Dipakai saat key device dicabut pengelola. */
    suspend fun forgetDevice() {
        cache = null
        context.sessionStore.edit { prefs ->
            prefs.remove(Keys.data)
            prefs.remove(Keys.deviceId)
        }
    }
}
