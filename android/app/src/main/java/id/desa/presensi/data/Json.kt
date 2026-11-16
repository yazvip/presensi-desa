package id.desa.presensi.data

import kotlinx.serialization.json.Json as KtJson

/** Satu konfigurasi JSON untuk seluruh aplikasi. */
val Json: KtJson = KtJson {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    isLenient = true
}
