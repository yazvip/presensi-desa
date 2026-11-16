package id.desa.presensi.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import id.desa.presensi.data.GpsSample
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

/**
 * Ambil sampel GPS "best of 3": tiga pembacaan berjarak 1,5 detik, backend memilih
 * yang paling akurat. Satu sampel cepat mudah meleset puluhan meter di dalam gedung.
 */
class LocationSampler(private val context: Context) {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    suspend fun sample(): List<GpsSample> {
        if (!hasPermission()) return emptyList()

        val collected = mutableListOf<GpsSample>()
        withTimeoutOrNull(15.seconds) {
            updates()
                .take(3)
                .collect { location -> collected += location.toSample() }
        }

        if (collected.isEmpty()) {
            lastKnown()?.let { collected += it.toSample() }
        }
        return collected.distinct()
    }

    private fun Location.toSample() = GpsSample(
        lat = latitude,
        lng = longitude,
        acc = if (hasAccuracy()) accuracy.toDouble() else 999.0,
    )

    @SuppressLint("MissingPermission")
    private suspend fun lastKnown(): Location? = suspendCancellableCoroutine { cont ->
        try {
            client.lastLocation
                .addOnSuccessListener { location -> if (cont.isActive) cont.resume(location) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        } catch (e: SecurityException) {
            if (cont.isActive) cont.resume(null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun updates(): Flow<Location> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L)
            .setMinUpdateIntervalMillis(1000L)
            .setMaxUpdates(8)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            close(e)
        }

        awaitClose { client.removeLocationUpdates(callback) }
    }
}
