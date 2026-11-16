package id.desa.presensi.ui.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import id.desa.presensi.data.ServiceLocator
import id.desa.presensi.qr.QrScannerView
import id.desa.presensi.ui.UiState
import id.desa.presensi.ui.theme.LedgerCard
import id.desa.presensi.ui.theme.LocalAccents
import id.desa.presensi.ui.theme.NoticeBanner
import id.desa.presensi.ui.theme.PrimaryButton
import id.desa.presensi.ui.theme.SectionLabel

@Composable
fun ScannerScreen(
    state: UiState,
    onQrDetected: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalAccents.current
    var cameraGranted by remember { mutableStateOf(false) }
    var locationGranted by remember {
        mutableStateOf(ServiceLocator.location.hasPermission())
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> cameraGranted = granted }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        locationGranted = result.values.any { it } || ServiceLocator.location.hasPermission()
    }

    LaunchedEffect(Unit) {
        cameraLauncher.launch(Manifest.permission.CAMERA)
        if (!locationGranted) {
            locationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    val preview = state.preview

    Column(modifier = modifier.fillMaxSize().padding(20.dp)) {
        SectionLabel(if (preview == null) "Pindai QR kantor" else "Konfirmasi absensi")
        Spacer(Modifier.height(10.dp))

        if (preview == null) {
            if (cameraGranted) {
                QrScannerView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp)),
                    onQrDetected = { value -> if (!state.busy) onQrDetected(value) },
                )
            } else {
                NoticeBanner(
                    message = "Izin kamera diperlukan untuk memindai QR kantor desa.",
                    isError = true,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Minta izin kamera")
                }
            }
        } else {
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LedgerCard {
                    Column {
                        Text(preview.officeName, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "${preview.distanceM} m",
                            style = MaterialTheme.typography.displayMedium,
                            color = accents.emerald,
                        )
                        Text(
                            text = "dari titik kantor · radius ${preview.radiusM} m · akurasi ${preview.accM} m",
                            style = MaterialTheme.typography.bodySmall,
                            color = accents.muted,
                        )
                    }
                }
            }
        }

        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            NoticeBanner(message = state.error, isError = true)
        }

        Spacer(Modifier.height(16.dp))
        if (preview != null) {
            PrimaryButton(
                text = if (state.hasCheckedIn) "Konfirmasi Absen Pulang" else "Konfirmasi Absen Masuk",
                loading = state.busy,
                enabled = preview.inRange,
                onClick = onConfirm,
            )
            Spacer(Modifier.height(8.dp))
        }
        TextButton(onClick = onCancel, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (preview != null) "Batal" else "Tutup")
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Absensi sah hanya bila Anda berada di dalam radius kantor desa.",
            style = MaterialTheme.typography.bodySmall,
            color = accents.muted,
        )
    }
}
