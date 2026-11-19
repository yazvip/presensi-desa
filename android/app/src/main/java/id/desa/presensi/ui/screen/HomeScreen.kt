package id.desa.presensi.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import id.desa.presensi.data.ThemeMode
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
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WIB: ZoneId = ZoneId.of("Asia/Jakarta")
private val clockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.forLanguageTag("id-ID"))

@Composable
fun HomeScreen(
    state: UiState,
    onScan: () -> Unit,
    onHistory: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalAccents.current
    val session = state.session
    val context = LocalContext.current

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onToggleReminder(true)
    }

    val now by produceState(initialValue = LocalDateTime.now(WIB)) {
        while (true) {
            value = LocalDateTime.now(WIB)
            delay(1000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 22.dp),
    ) {
        // Header eksekutif
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Monogram(size = 46)
                Spacer(Modifier.width(12.dp))
                Column {
                    SectionLabel(greeting(now.hour))
                    Text(
                        text = session?.nama ?: "Petugas Desa",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (!session?.jabatan.isNullOrBlank()) {
                        Text(
                            text = session?.jabatan ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = accents.muted,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onToggleTheme,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, accents.hairline),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = accents.goldDeep,
                    ),
                ) {
                    Text(
                        text = when (state.themeMode) {
                            ThemeMode.SYSTEM -> "Auto"
                            ThemeMode.LIGHT -> "Terang"
                            ThemeMode.DARK -> "Gelap"
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                OutlinedButton(
                    onClick = onSignOut,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, accents.hairline),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = accents.muted,
                    ),
                ) {
                    Text("Keluar", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // Kartu Hero Jam & Tanggal
        HeroEmeraldCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "WAKTU SERVER WIB",
                        style = MaterialTheme.typography.labelSmall,
                        color = accents.goldLight,
                    )
                    StatusPill(
                        text = when {
                            state.doneForToday -> "Selesai"
                            state.hasCheckedIn -> "Bertugas"
                            else -> "Siap Masuk"
                        },
                        tone = PillTone.GoldDarkBg,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = now.format(clockFormatter),
                    style = MaterialTheme.typography.displayLarge,
                    color = Ivory,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = now.format(dateFormatter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ivory.copy(alpha = 0.78f),
                )
                Spacer(Modifier.height(16.dp))
                GoldRule(width = 64)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Kartu Status Kehadiran Hari Ini
        LedgerCard(highlighted = state.today.isNotEmpty()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel("Catatan hari ini")
                    Text(
                        text = "${state.today.size}/2 sesi",
                        style = MaterialTheme.typography.bodySmall,
                        color = accents.muted,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(12.dp))

                if (state.today.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(accents.gold),
                        )
                        Text(
                            text = "Belum ada presensi hari ini. Pindai QR resmi di kantor desa untuk absen masuk.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = accents.muted,
                        )
                    }
                } else {
                    state.today.forEachIndexed { index, row ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = accents.hairline,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (row.type == "masuk") {
                                                accents.success.copy(alpha = 0.12f)
                                            } else {
                                                accents.gold.copy(alpha = 0.16f)
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if (row.type == "masuk") "IN" else "OUT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (row.type == "masuk") accents.success else accents.goldDeep,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Column {
                                    Text(
                                        text = if (row.type == "masuk") "Absen Masuk" else "Absen Pulang",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = "${row.timeLabel()} · ${row.officeName} · ${row.distanceM} m",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = accents.muted,
                                    )
                                }
                            }
                            StatusPill(
                                text = row.statusLabel(),
                                tone = when (row.status) {
                                    "tepat_waktu" -> PillTone.Success
                                    "terlambat", "pulang_cepat" -> PillTone.Warning
                                    else -> PillTone.Neutral
                                },
                            )
                        }
                    }
                }
            }
        }

        if (state.error != null) {
            Spacer(Modifier.height(14.dp))
            NoticeBanner(message = state.error, isError = true)
        }
        if (state.message != null) {
            Spacer(Modifier.height(14.dp))
            NoticeBanner(message = state.message, isError = false)
        }

        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            text = when {
                state.doneForToday -> "Absensi Hari Ini Selesai"
                state.hasCheckedIn -> "Pindai QR · Absen Pulang"
                else -> "Pindai QR · Absen Masuk"
            },
            enabled = !state.doneForToday,
            loading = state.busy,
            onClick = onScan,
        )

        Spacer(Modifier.height(14.dp))
        LedgerCard {
            Column {
                SectionLabel("Profil & Preferensi")
                Spacer(Modifier.height(10.dp))
                KeyValueRow("Jabatan", session?.jabatan ?: "-")
                HorizontalDivider(color = accents.hairline.copy(alpha = 0.6f))
                KeyValueRow("NIPD", session?.nipd ?: "-")
                HorizontalDivider(color = accents.hairline.copy(alpha = 0.6f))
                KeyValueRow("Batas Perangkat", "${session?.deviceLimit ?: 1} perangkat")
                HorizontalDivider(color = accents.hairline.copy(alpha = 0.6f))
                KeyValueRow("Validasi Lokasi", "QR Kantor + Radius 50 m")
                HorizontalDivider(color = accents.hairline.copy(alpha = 0.6f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pengingat Absen Harian", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Notifikasi pukul 07:15 & 15:45 WIB",
                            style = MaterialTheme.typography.bodySmall,
                            color = accents.muted,
                        )
                    }
                    Switch(
                        checked = state.reminderEnabled,
                        onCheckedChange = { checked ->
                            if (!checked) {
                                onToggleReminder(false)
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    onToggleReminder(true)
                                } else {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                onToggleReminder(true)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Ivory,
                            checkedTrackColor = accents.gold,
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SecondaryOutlineButton(
            text = "Lihat Riwayat Kehadiran",
            onClick = onHistory,
        )
        Spacer(Modifier.height(8.dp))
    }
}

private fun greeting(hour: Int): String = when (hour) {
    in 4..10 -> "Selamat pagi"
    in 11..14 -> "Selamat siang"
    in 15..17 -> "Selamat sore"
    else -> "Selamat malam"
}
