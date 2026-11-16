package id.desa.presensi.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DevicePayload(
    val publicKey: String,
    val label: String? = null,
    val androidIdHash: String? = null,
    val strongbox: Boolean = false,
    val model: String? = null,
    val manufacturer: String? = null,
    val osVersion: String? = null,
    val appVersion: String? = null,
    val integrityVerdict: String? = null,
)

@Serializable
data class ActivateRequest(
    val nipd: String,
    val code: String,
    val pin: String,
    val device: DevicePayload,
)

@Serializable
data class ActivateDeviceRequest(
    val nipd: String,
    val pin: String,
    val device: DevicePayload,
)

@Serializable
data class ChallengeRequest(val nipd: String, val deviceId: String)

@Serializable
data class ChallengeResponse(val nonce: String, val algorithm: String = "SHA256withECDSA")

@Serializable
data class LoginRequest(
    val nipd: String,
    val deviceId: String,
    val signature: String,
    val pin: String? = null,
)

@Serializable
data class UserDto(
    val id: String,
    val nama: String,
    val role: String,
    val nipd: String,
    val jabatan: String? = null,
)

@Serializable
data class BoundDeviceDto(val id: String, val label: String? = null, val deviceLimit: Int = 1)

@Serializable
data class SessionResponse(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresIn: Int = 900,
    val refreshExpiresAt: String? = null,
    val user: UserDto,
    val device: BoundDeviceDto? = null,
)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class RefreshResponse(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresIn: Int = 900,
)

@Serializable
data class GpsSample(val lat: Double, val lng: Double, val acc: Double)

@Serializable
data class AttendanceRequest(
    val qr: String,
    val samples: List<GpsSample>,
    val deviceClock: String? = null,
    val source: String = "online",
)

@Serializable
data class AttendanceDto(
    val id: String,
    val type: String,
    val tsServer: String,
    val workDate: String,
    val status: String,
    val distanceM: Int,
    val accM: Int,
    val officeName: String,
)

@Serializable
data class AttendanceResponse(val ok: Boolean, val attendance: AttendanceDto)

@Serializable
data class SummaryDto(val status: String, val count: Int)

@Serializable
data class HistoryResponse(val items: List<AttendanceDto>, val summary: List<SummaryDto>)

@Serializable
data class PreviewRequest(val qr: String, val samples: List<GpsSample>)

@Serializable
data class PreviewResponse(
    val officeName: String,
    val distanceM: Int,
    val radiusM: Int,
    val accM: Int,
    val accMaxM: Int = 60,
    val inRange: Boolean,
    val reason: String? = null,
)

@Serializable
data class DeviceListResponse(val devices: List<DeviceRowDto>, val deviceLimit: Int = 1)

@Serializable
data class DeviceRowDto(
    val id: String,
    val label: String? = null,
    val model: String? = null,
    val manufacturer: String? = null,
    @SerialName("os_version") val osVersion: String? = null,
    @SerialName("first_bound_at") val firstBoundAt: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    val status: String = "active",
)

@Serializable
data class LogoutResponse(val ok: Boolean = true)

@Serializable
data class ApiErrorBody(val code: String = "ERROR", val message: String = "Terjadi kesalahan.")
