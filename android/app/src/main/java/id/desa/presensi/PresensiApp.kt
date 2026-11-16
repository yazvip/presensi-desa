package id.desa.presensi

import android.app.Application
import id.desa.presensi.data.ServiceLocator

class PresensiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
