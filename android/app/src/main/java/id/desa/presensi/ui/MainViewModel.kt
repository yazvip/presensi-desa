package id.desa.presensi.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.desa.presensi.data.ApiException
import id.desa.presensi.data.AttendanceDto
import id.desa.presensi.data.PreviewResponse
import id.desa.presensi.data.ServiceLocator
import id.desa.presensi.data.Session
import id.desa.presensi.data.ThemeMode
import id.desa.presensi.reminder.AttendanceReminder
import id.desa.presensi.update.AppUpdateInfo
import id.desa.presensi.update.AppUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

private val WIB: ZoneId = ZoneId.of("Asia/Jakarta")

data class UiState(
    val booting: Boolean = true,
    val session: Session? = null,
    val busy: Boolean = false,
    val scanning: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val history: List<AttendanceDto> = emptyList(),
    val today: List<AttendanceDto> = emptyList(),
    val preview: PreviewResponse? = null,
    val lastResult: AttendanceDto? = null,
    /** QR yang sudah terbaca, menunggu konfirmasi petugas. */
    val pendingQr: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val reminderEnabled: Boolean = false,
    val availableUpdate: AppUpdateInfo? = null,
) {
    val hasCheckedIn: Boolean get() = today.any { it.type == "masuk" }
    val hasCheckedOut: Boolean get() = today.any { it.type == "pulang" }
    val doneForToday: Boolean get() = hasCheckedIn && hasCheckedOut
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.repo
    private val session = ServiceLocator.session
    private val location = ServiceLocator.location

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val current = runCatching { session.current() }.getOrNull()
            val theme = runCatching { session.themeMode() }.getOrDefault(ThemeMode.SYSTEM)
            val reminder = runCatching { session.reminderEnabled() }.getOrDefault(false)
            _state.value = _state.value.copy(
                booting = false,
                session = current,
                themeMode = theme,
                reminderEnabled = reminder,
            )
            if (current != null) refreshHistory()
            val update = AppUpdater.checkLatest()
            if (update != null) {
                _state.value = _state.value.copy(availableUpdate = update)
            }
        }
    }

    fun dismissUpdate() {
        _state.value = _state.value.copy(availableUpdate = null)
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, message = null, lastResult = null)
    }

    fun startScanning() {
        _state.value = _state.value.copy(scanning = true, preview = null, error = null, lastResult = null)
    }

    fun stopScanning() {
        _state.value = _state.value.copy(scanning = false, preview = null, pendingQr = null)
    }

    fun setThemeMode(mode: ThemeMode) {
        _state.value = _state.value.copy(themeMode = mode)
        viewModelScope.launch { session.setThemeMode(mode) }
    }

    fun cycleTheme() {
        val next = when (_state.value.themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        _state.value = _state.value.copy(themeMode = next)
        viewModelScope.launch { session.setThemeMode(next) }
    }

    fun setReminderEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(reminderEnabled = enabled)
        val app = getApplication<Application>()
        if (enabled) {
            AttendanceReminder.scheduleDaily(app)
        } else {
            AttendanceReminder.cancel(app)
        }
        viewModelScope.launch { session.setReminderEnabled(enabled) }
    }

    fun login(nipd: String, pin: String, onDone: () -> Unit) = launchBusy {
        val saved = repo.login(nipd, pin)
        _state.value = _state.value.copy(session = saved, message = "Selamat datang, ${saved.nama}.")
        refreshHistory()
        onDone()
    }

    fun activate(nipd: String, code: String, pin: String, label: String?, onDone: () -> Unit) = launchBusy {
        val saved = repo.registerDevice(nipd, code, pin, label)
        _state.value = _state.value.copy(session = saved, message = "Perangkat berhasil diaktifkan.")
        refreshHistory()
        onDone()
    }

    /** Pindai QR → cek jarak dulu, baru minta konfirmasi. */
    fun onQrScanned(qr: String) = launchBusy {
        _state.value = _state.value.copy(scanning = false)
        if (!location.hasPermission()) {
            throw ApiException("NO_PERMISSION", "Izin lokasi diperlukan untuk absensi.", 0)
        }
        if (!location.isLocationEnabled()) {
            throw ApiException("GPS_OFF", "GPS HP masih mati. Nyalakan lalu pindai ulang.", 0)
        }
        val samples = location.sample()
        val preview = repo.preview(qr, samples)
        _state.value = _state.value.copy(
            preview = preview,
            message = if (preview.inRange) {
                "Anda ${preview.distanceM} m dari kantor — siap absen."
            } else {
                null
            },
            error = if (preview.inRange) null else distanceMessage(preview),
            pendingQr = qr,
        )
    }

    fun confirmAttendance() = launchBusy {
        val qr = _state.value.pendingQr ?: throw ApiException("NO_QR", "QR belum terbaca.", 0)
        val samples = location.sample()
        val result = repo.submit(qr, samples)
        _state.value = _state.value.copy(
            preview = null,
            pendingQr = null,
            lastResult = result,
            message = if (result.type == "masuk") "Absen masuk tercatat ${result.statusLabel()}."
            else "Absen pulang tercatat ${result.statusLabel()}.",
        )
        refreshHistory()
    }

    fun refreshHistory() {
        viewModelScope.launch {
            if (_state.value.session == null) return@launch
            runCatching { repo.history() }
                .onSuccess { res ->
                    val today = LocalDate.now(WIB).toString()
                    _state.value = _state.value.copy(
                        history = res.items,
                        today = res.items.filter { it.workDate == today },
                    )
                }
                .onFailure { err ->
                    if (err is ApiException && (err.status == 401 || err.code == "DEVICE_REVOKED")) {
                        signOut()
                    }
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { repo.logout() }
            _state.value = _state.value.copy(
                session = null,
                history = emptyList(),
                today = emptyList(),
                preview = null,
                lastResult = null,
                pendingQr = null,
                message = null,
                error = null,
            )
        }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, message = null)
            try {
                block()
            } catch (e: ApiException) {
                _state.value = _state.value.copy(error = e.message, scanning = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = e.message ?: "Terjadi kesalahan tak terduga.",
                    scanning = false,
                )
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

    private fun distanceMessage(p: PreviewResponse): String = when (p.reason) {
        "OUT_OF_RANGE" -> "Anda ${p.distanceM} m dari kantor desa (maksimal ${p.radiusM} m)."
        "ACCURACY_TOO_LOW" -> "Akurasi GPS ${p.accM} m — terlalu rendah. Coba di area terbuka."
        else -> "Lokasi belum bisa dipastikan. Coba lagi sebentar."
    }
}

fun AttendanceDto.statusLabel(): String = when (status) {
    "tepat_waktu" -> "tepat waktu"
    "terlambat" -> "terlambat"
    "pulang_cepat" -> "pulang lebih awal"
    "luar_radius_review" -> "perlu ditinjau"
    else -> status.replace('_', ' ')
}

fun AttendanceDto.timeLabel(): String {
    val instant = runCatching { java.time.Instant.parse(tsServer) }.getOrNull() ?: return "--:--"
    return java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        .withZone(WIB)
        .format(instant)
}
