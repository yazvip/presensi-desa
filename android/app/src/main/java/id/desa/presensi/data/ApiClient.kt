package id.desa.presensi.data

import id.desa.presensi.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ApiException(
    val code: String,
    override val message: String,
    val status: Int,
) : Exception(message)

/** Klien HTTP tipis di atas OkHttp — tanpa refleksi, mudah dibaca ulang. */
class ApiService(private val session: SessionStore) {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            // cached() tidak menyentuh disk; current() hanya dipakai saat cache masih kosong.
            val token = session.cached()?.accessToken
                ?: runBlocking { session.current()?.accessToken }
            val request = chain.request().newBuilder()
                .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
                .build()
            chain.proceed(request)
        }
        .build()

    private fun url(path: String): String =
        BuildConfig.API_BASE_URL.trimEnd('/') + "/" + path.trimStart('/')

    /** Satu tempat untuk semua urusan jaringan; hasilnya teks mentah. */
    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val parsed = runCatching { Json.decodeFromString<ApiErrorBody>(text) }.getOrNull()
                throw ApiException(
                    parsed?.code ?: "HTTP_${response.code}",
                    parsed?.message ?: "Server menolak permintaan (${response.code}).",
                    response.code,
                )
            }
            text
        }
    }

    private inline fun <reified Res : Any> decode(text: String): Res = try {
        Json.decodeFromString<Res>(text)
    } catch (e: Exception) {
        throw ApiException("BAD_RESPONSE", "Jawaban server tidak dikenali.", 0)
    }

    private suspend inline fun <reified Req : Any, reified Res : Any> post(
        path: String,
        body: Req,
    ): Res {
        val payload = Json.encodeToString(body).toRequestBody(jsonMedia)
        val request = Request.Builder().url(url(path)).post(payload).build()
        return decode(execute(request))
    }

    private suspend inline fun <reified Res : Any> get(path: String): Res {
        val request = Request.Builder().url(url(path)).get().build()
        return decode(execute(request))
    }

    suspend fun activate(body: ActivateRequest): SessionResponse = post("auth/activate", body)

    suspend fun activateDevice(body: ActivateDeviceRequest): SessionResponse =
        post("auth/activate-device", body)

    suspend fun challenge(body: ChallengeRequest): ChallengeResponse = post("auth/challenge", body)

    suspend fun login(body: LoginRequest): SessionResponse = post("auth/login", body)

    suspend fun refresh(body: RefreshRequest): RefreshResponse = post("auth/refresh", body)

    suspend fun logout(body: RefreshRequest) {
        runCatching { post<RefreshRequest, LogoutResponse>("auth/logout", body) }
    }

    suspend fun submitAttendance(body: AttendanceRequest): AttendanceResponse =
        post("attendance", body)

    suspend fun previewAttendance(body: PreviewRequest): PreviewResponse =
        post("attendance/preview", body)

    suspend fun history(): HistoryResponse = get("attendance/me")

    suspend fun devices(): DeviceListResponse = get("auth/devices")
}
