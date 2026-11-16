package id.desa.presensi.device

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import id.desa.presensi.BuildConfig
import java.security.MessageDigest

/** Identitas perangkat yang dikirim saat aktivasi. */
object DeviceInfo {

    val manufacturer: String get() = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
    val model: String get() = Build.MODEL.orEmpty()
    val osVersion: String get() = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    val appVersion: String get() = BuildConfig.VERSION_NAME

    /**
     * ANDROID_ID di-hash — nilainya per aplikasi + per pengguna, jadi tidak bisa
     * dipakai melacak lintas aplikasi. IMEI tidak tersedia sejak Android 10.
     */
    @SuppressLint("HardwareIds")
    fun androidIdHash(context: Context): String? {
        val raw = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (raw.isNullOrBlank()) return null
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
