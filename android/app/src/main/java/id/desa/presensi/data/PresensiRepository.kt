package id.desa.presensi.data

import android.content.Context
import id.desa.presensi.device.DeviceInfo
import id.desa.presensi.device.DeviceKeyManager
import java.io.IOException
import java.time.Instant

/** Semua aturan bisnis sisi klien yang aman dikerjakan di HP — sisanya di server. */
class PresensiRepository(
    private val api: ApiService,
    private val session: SessionStore,
    private val deviceKeys: DeviceKeyManager,
    private val context: Context,
) {

    /** Pastikan kunci ada, lalu bungkus jadi payload aktivasi. */
    fun devicePayload(label: String? = null): DevicePayload? {
        deviceKeys.ensureKey() ?: return null
        val publicKey = deviceKeys.publicKeyBase64() ?: return null
        return DevicePayload(
            publicKey = publicKey,
            label = label,
            androidIdHash = DeviceInfo.androidIdHash(context),
            strongbox = deviceKeys.strongbox,
            model = DeviceInfo.model,
            manufacturer = DeviceInfo.manufacturer,
            osVersion = DeviceInfo.osVersion,
            appVersion = DeviceInfo.appVersion,
        )
    }

    suspend fun registerDevice(nipd: String, code: String, pin: String, label: String?): Session {
        val payload = devicePayload(label)
            ?: throw ApiException("KEYSTORE_UNAVAILABLE", "Perangkat ini tidak mendukung kunci aman. Hubungi pengelola.", 0)
        val res = api.activate(ActivateRequest(nipd.trim(), code.trim().uppercase(), pin, payload))
        return persist(res, res.device?.id)
    }

    suspend fun addDevice(nipd: String, pin: String, label: String?): Session {
        val payload = devicePayload(label)
            ?: throw ApiException("KEYSTORE_UNAVAILABLE", "Perangkat ini tidak mendukung kunci aman.", 0)
        val res = api.activateDevice(ActivateDeviceRequest(nipd.trim(), pin, payload))
        return persist(res, res.device?.id)
    }

    /** Login: ambil nonce, tanda tangani dengan kunci Keystore, kirim PIN + signature. */
    suspend fun login(nipd: String, pin: String): Session = withRefresh {
        val deviceId = session.deviceId()
        val key = deviceKeys.ensureKey()
        if (deviceId == null || key == null) {
            throw ApiException(
                "ACTIVATION_REQUIRED",
                "HP ini belum terdaftar. Minta kode aktivasi kepada Pengelola Desa.",
                0,
            )
        }
        try {
            val challenge = api.challenge(ChallengeRequest(nipd.trim(), deviceId))
            val signature = deviceKeys.sign(challenge.nonce)
                ?: throw ApiException(
                    "KEYSTORE_LOCKED",
                    "Kunci perangkat tidak bisa dipakai. Buka layar kunci HP lalu coba lagi.",
                    0,
                )
            val res = api.login(LoginRequest(nipd.trim(), deviceId, signature, pin))
            persist(res, deviceId)
        } catch (e: ApiException) {
            if (e.code == "DEVICE_REVOKED" || e.code == "DEVICE_ALREADY_BOUND") {
                session.forgetDevice()
                deviceKeys.deleteKey()
            }
            throw e
        }
    }

    suspend fun preview(qr: String, samples: List<GpsSample>): PreviewResponse = withRefresh {
        if (samples.isEmpty()) {
            throw ApiException("NO_LOCATION", "Lokasi GPS belum terbaca. Cari area terbuka lalu coba lagi.", 0)
        }
        api.previewAttendance(PreviewRequest(qr, samples))
    }

    suspend fun submit(qr: String, samples: List<GpsSample>): AttendanceDto = withRefresh {
        if (samples.isEmpty()) {
            throw ApiException("NO_LOCATION", "Lokasi GPS belum terbaca. Cari area terbuka lalu coba lagi.", 0)
        }
        val res = api.submitAttendance(
            AttendanceRequest(
                qr = qr,
                samples = samples,
                deviceClock = Instant.now().toString(),
                source = "online",
            ),
        )
        res.attendance
    }

    suspend fun history(): HistoryResponse = withRefresh { api.history() }

    suspend fun logout() {
        val refresh = session.current()?.refreshToken
        if (!refresh.isNullOrBlank()) api.logout(RefreshRequest(refresh))
        session.clear()
    }

    /** Satu kali percobaan ulang bila access token (15 menit) sudah kedaluwarsa. */
    private suspend fun <T> withRefresh(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: ApiException) {
            if (e.status != 401) throw e
            val refresh = (session.cached()?.refreshToken ?: session.current()?.refreshToken) ?: throw e
            val rotated = try {
                api.refresh(RefreshRequest(refresh))
            } catch (err: ApiException) {
                if (err.status == 401 || err.status == 403) {
                    session.clear()
                }
                throw err
            }
            session.updateTokens(rotated.accessToken, rotated.refreshToken)
            block()
        } catch (e: IOException) {
            throw ApiException("OFFLINE", "Tidak ada koneksi internet. Sambungkan HP lalu coba lagi.", 0)
        }
    }

    private suspend fun persist(res: SessionResponse, deviceId: String?): Session {
        val saved = Session(
            userId = res.user.id,
            nama = res.user.nama,
            role = res.user.role,
            nipd = res.user.nipd,
            jabatan = res.user.jabatan,
            deviceId = deviceId ?: session.deviceId(),
            deviceLimit = res.device?.deviceLimit ?: 1,
            accessToken = res.accessToken,
            refreshToken = res.refreshToken,
        )
        session.save(saved)
        return saved
    }
}
