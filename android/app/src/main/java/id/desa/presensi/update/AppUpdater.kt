package id.desa.presensi.update

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import id.desa.presensi.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val downloadUrl: String,
)

@Serializable
private data class GitHubAsset(
    val name: String = "",
    val browser_download_url: String = "",
)

@Serializable
private data class GitHubRelease(
    val tag_name: String = "",
    val body: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

object AppUpdater {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/yazvip/presensi-desa/releases/latest"

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun checkLatest(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@runCatching null
                val raw = res.body?.string().orEmpty()
                val release = json.decodeFromString(GitHubRelease.serializer(), raw)
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                    ?: return@runCatching null

                val remoteCode = parseVersionCode(release.tag_name)
                if (remoteCode <= BuildConfig.VERSION_CODE) return@runCatching null

                AppUpdateInfo(
                    versionCode = remoteCode,
                    versionName = release.tag_name.ifBlank { "v1.0.$remoteCode" },
                    notes = release.body?.trim()?.takeIf { it.isNotEmpty() }
                        ?: "Tersedia versi terbaru aplikasi Presensi Desa.",
                    downloadUrl = apkAsset.browser_download_url,
                )
            }
        }.getOrNull()
    }

    private fun parseVersionCode(tag: String): Int {
        // Contoh tag: v1.0.12 -> 12
        val last = tag.trim().removePrefix("v").split('.').lastOrNull() ?: return 0
        return last.toIntOrNull() ?: 0
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun downloadAndInstall(context: Context, info: AppUpdateInfo) {
        val appCtx = context.applicationContext
        val fileName = "presensi-desa-update.apk"
        val outFile = File(appCtx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (outFile.exists()) outFile.delete()

        val dm = appCtx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (dm == null) {
            openBrowser(appCtx, info.downloadUrl)
            return
        }

        val req = DownloadManager.Request(Uri.parse(info.downloadUrl))
            .setTitle("Memperbarui Presensi Desa ${info.versionName}")
            .setDescription("Mengunduh paket pembaruan aplikasi...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(appCtx, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadId = runCatching { dm.enqueue(req) }.getOrElse {
            openBrowser(appCtx, info.downloadUrl)
            return
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != downloadId) return
                runCatching { appCtx.unregisterReceiver(this) }
                installApk(appCtx, outFile, info.downloadUrl)
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(appCtx, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        } else {
            appCtx.registerReceiver(receiver, filter)
        }
    }

    private fun installApk(context: Context, file: File, fallbackUrl: String) {
        if (!file.exists()) {
            openBrowser(context, fallbackUrl)
            return
        }
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: run {
            openBrowser(context, fallbackUrl)
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        runCatching { context.startActivity(intent) }
            .onFailure { openBrowser(context, fallbackUrl) }
    }

    private fun openBrowser(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(intent) }
    }
}
