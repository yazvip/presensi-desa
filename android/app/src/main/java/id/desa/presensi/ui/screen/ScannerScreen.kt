package id.desa.presensi.ui.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import id.desa.presensi.data.ServiceLocator
import id.desa.presensi.qr.QrScannerView
import id.desa.presensi.ui.UiState
import id.desa.presensi.ui.statusLabel
import id.desa.presensi.ui.theme.GoldRule
import id.desa.presensi.ui.theme.HeroEmeraldCard
import id.desa.presensi.ui.theme.Ivory
import id.desa.presensi.ui.theme.KeyValueRow
import id.desa.presensi.ui.theme.LedgerCard
import id.desa.presensi.ui.theme.LocalAccents
import id.desa.presensi.ui.theme.Monogram
import id.desa.presensi.ui.theme.NoticeBanner
import id.desa.presensi.ui.theme.PillTone
import id.desa.presensi.ui.theme.PrimaryButton
import id.desa.presensi.ui.theme.SecondaryOutlineButton
import id.desa.presensi.ui.theme.SectionLabel
import id.desa.presensi.ui.theme.StatusPill
import id.desa.presensi.ui.timeLabel

@Composable
fun ScannerScreen(
    state: UiState,
    onQrDetected: (String) -> Unit,
    onConfirm: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalAccents.current
    val haptic = LocalHapticFeedback.current
    var cameraGranted by remember { mutableStateOf(false) }
    var locationGranted by remember {
        mutableStateOf(ServiceLocator.location.hasPermission())
    }
    var torchEnabled by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }

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

    LaunchedEffect(state.lastResult) {
        if (state.lastResult != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val preview = state.preview
    val result = state.lastResult

    val infinite = rememberInfiniteTransition(label = "scanBeam")
    val beamProgress by infinite.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "beamProgress",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        // Header layar pindai
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                SectionLabel(
                    when {
                        result != null -> "Presensi Berhasil"
                        preview != null -> "Verifikasi Lokasi"
                        else -> "Pemindaian QR"
                    },
                )
                Text(
                    text = when {
                        result != null -> "Catatan Tersimpan"
                        preview != null -> "Konfirmasi Absensi"
                        else -> "Pindai QR Kantor"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            if (result == null) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !state.busy,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, accents.hairline),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = accents.muted,
                    ),
                ) {
                    Text(if (preview != null) "Batal" else "Tutup", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            result != null -> {
                // Modal / kartu sukses penuh layar
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    HeroEmeraldCard {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Monogram(size = 64, label = "OK")
                            Spacer(Modifier.height(16.dp))
                            StatusPill(
                                text = result.statusLabel(),
                                tone = PillTone.GoldDarkBg,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (result.type == "masuk") "Absen Masuk Tercatat" else "Absen Pulang Tercatat",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Ivory,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "${result.timeLabel()} WIB · ${result.officeName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Ivory.copy(alpha = 0.82f),
                            )
                            Spacer(Modifier.height(18.dp))
                            GoldRule(width = 64)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    LedgerCard {
                        Column {
                            SectionLabel("Ringkasan Verifikasi")
                            Spacer(Modifier.height(8.dp))
                            KeyValueRow("Tanggal Kerja", result.workDate)
                            HorizontalDivider(color = accents.hairline.copy(alpha = 0.6f))
                            KeyValueRow("Jarak dari Kantor", "${result.distanceM} m")
                            HorizontalDivider(color = accents.hairline.copy(alpha = 0.6f))
                            KeyValueRow("Akurasi GPS", "${result.accM} m")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = "Kembali ke Beranda",
                    onClick = onDone,
                )
            }

            preview == null -> {
                if (cameraGranted) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .border(1.5.dp, accents.gold.copy(alpha = 0.55f), RoundedCornerShape(28.dp)),
                    ) {
                        QrScannerView(
                            modifier = Modifier.fillMaxSize(),
                            torchEnabled = torchEnabled,
                            onHasFlashChanged = { hasFlash = it },
                            onQrDetected = { value ->
                                if (!state.busy) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onQrDetected(value)
                                }
                            },
                        )

                        // Bingkai sudut emas + garis laser scan animasi
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val boxSize = size.minDimension * 0.64f
                            val left = (size.width - boxSize) / 2f
                            val top = (size.height - boxSize) / 2f
                            val right = left + boxSize
                            val bottom = top + boxSize
                            val corner = 34.dp.toPx()
                            val stroke = 3.5.dp.toPx()
                            val goldColor = accents.goldLight

                            // Kiri atas
                            drawLine(goldColor, Offset(left, top), Offset(left + corner, top), stroke, StrokeCap.Round)
                            drawLine(goldColor, Offset(left, top), Offset(left, top + corner), stroke, StrokeCap.Round)
                            // Kanan atas
                            drawLine(goldColor, Offset(right - corner, top), Offset(right, top), stroke, StrokeCap.Round)
                            drawLine(goldColor, Offset(right, top), Offset(right, top + corner), stroke, StrokeCap.Round)
                            // Kiri bawah
                            drawLine(goldColor, Offset(left, bottom - corner), Offset(left, bottom), stroke, StrokeCap.Round)
                            drawLine(goldColor, Offset(left, bottom), Offset(left + corner, bottom), stroke, StrokeCap.Round)
                            // Kanan bawah
                            drawLine(goldColor, Offset(right - corner, bottom), Offset(right, bottom), stroke, StrokeCap.Round)
                            drawLine(goldColor, Offset(right, bottom - corner), Offset(right, bottom), stroke, StrokeCap.Round)

                            // Garis laser bergerak
                            val beamY = top + (boxSize * beamProgress)
                            drawLine(
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        accents.goldLight.copy(alpha = 0.90f),
                                        Color.Transparent,
                                    ),
                                    startX = left + 12f,
                                    endX = right - 12f,
                                ),
                                start = Offset(left + 12f, beamY),
                                end = Offset(right - 12f, beamY),
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }

                        // Tombol Senter (Flash) jika perangkat mendukung
                        if (hasFlash) {
                            OutlinedButton(
                                onClick = { torchEnabled = !torchEnabled },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(14.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (torchEnabled) accents.goldLight else Color.White.copy(alpha = 0.35f),
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (torchEnabled) {
                                        accents.gold.copy(alpha = 0.85f)
                                    } else {
                                        Color.Black.copy(alpha = 0.55f)
                                    },
                                    contentColor = Ivory,
                                ),
                            ) {
                                Text(
                                    text = if (torchEnabled) "Senter: Nyala" else "Senter",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }

                        // Pill instruksi di bagian bawah viewfinder
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.Black.copy(alpha = 0.62f))
                                .border(1.dp, accents.gold.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = "Arahkan kamera ke QR Code Kantor Desa",
                                style = MaterialTheme.typography.bodySmall,
                                color = Ivory,
                            )
                        }
                    }
                } else {
                    LedgerCard(modifier = Modifier.weight(1f)) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            NoticeBanner(
                                message = "Izin kamera diperlukan untuk memindai QR kantor desa.",
                                isError = true,
                            )
                            Spacer(Modifier.height(16.dp))
                            PrimaryButton(
                                text = "Izinkan Akses Kamera",
                                onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) },
                            )
                        }
                    }
                }

                if (state.error != null) {
                    Spacer(Modifier.height(12.dp))
                    NoticeBanner(message = state.error, isError = true)
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Absensi sah hanya bila Anda berada di dalam radius resmi kantor desa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = accents.muted,
                )
            }

            else -> {
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    HeroEmeraldCard {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "TITIK KANTOR TERDETEKSI",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accents.goldLight,
                                )
                                StatusPill(
                                    text = if (preview.inRange) "Dalam Radius" else "Di Luar Radius",
                                    tone = if (preview.inRange) PillTone.GoldDarkBg else PillTone.Danger,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = preview.officeName,
                                style = MaterialTheme.typography.titleLarge,
                                color = Ivory,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "${preview.distanceM} m",
                                style = MaterialTheme.typography.displayLarge,
                                color = accents.goldLight,
                            )
                            Text(
                                text = "jarak Anda dari titik presensi kantor",
                                style = MaterialTheme.typography.bodySmall,
                                color = Ivory.copy(alpha = 0.78f),
                            )
                            Spacer(Modifier.height(16.dp))
                            GoldRule(width = 56)
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    LedgerCard {
                        Column {
                            SectionLabel("Detail pengukuran GPS")
                            Spacer(Modifier.height(8.dp))
                            KeyValueRow("Radius Maksimal", "${preview.radiusM} m")
                            HorizontalDivider(color = accents.hairline.copy(alpha = 0.6f))
                            KeyValueRow("Akurasi Sinyal", "${preview.accM} m")
                            HorizontalDivider(color = accents.hairline.copy(alpha = 0.6f))
                            KeyValueRow(
                                "Jenis Absensi",
                                if (state.hasCheckedIn) "Absen Pulang" else "Absen Masuk",
                            )
                        }
                    }
                }

                if (state.error != null) {
                    Spacer(Modifier.height(12.dp))
                    NoticeBanner(message = state.error, isError = true)
                }

                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = if (state.hasCheckedIn) "Konfirmasi Absen Pulang" else "Konfirmasi Absen Masuk",
                    loading = state.busy,
                    enabled = preview.inRange,
                    onClick = onConfirm,
                )
                Spacer(Modifier.height(10.dp))
                SecondaryOutlineButton(
                    text = "Pindai Ulang",
                    onClick = onCancel,
                    enabled = !state.busy,
                )
            }
        }
    }
}
