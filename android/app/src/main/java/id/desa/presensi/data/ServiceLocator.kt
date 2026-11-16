package id.desa.presensi.data

import android.app.Application
import id.desa.presensi.device.DeviceKeyManager
import id.desa.presensi.location.LocationSampler

/**
 * Injeksi dependensi sederhana — tanpa framework, tanpa refleksi.
 * Semua komponen berumur sama dengan proses aplikasi.
 */
object ServiceLocator {

    lateinit var app: Application
        private set

    lateinit var session: SessionStore
        private set

    lateinit var api: ApiService
        private set

    lateinit var deviceKeys: DeviceKeyManager
        private set

    lateinit var location: LocationSampler
        private set

    lateinit var repo: PresensiRepository
        private set

    fun init(application: Application) {
        app = application
        session = SessionStore(application)
        deviceKeys = DeviceKeyManager()
        location = LocationSampler(application)
        api = ApiService(session)
        repo = PresensiRepository(api, session, deviceKeys, application)
    }
}
