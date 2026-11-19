package id.desa.presensi.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.desa.presensi.data.ServiceLocator
import id.desa.presensi.ui.UiState
import id.desa.presensi.ui.statusLabel
import id.desa.presensi.ui.theme.HeroEmeraldCard
import id.desa.presensi.ui.theme.Ivory
import id.desa.presensi.ui.theme.LedgerCard
import id.desa.presensi.ui.theme.LocalAccents
import id.desa.presensi.ui.theme.Monogram
import id.desa.presensi.ui.theme.NoticeBanner
import id.desa.presensi.ui.theme.PillTone
import id.desa.presensi.ui.theme.PrimaryButton
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
    modifier: Modifier = Modifier,
) {
    val accents = LocalAccents.current
    val session = state.session

    val now by produceState(initialValue = LocalDateTime.now(WIB)) {
        while (true) {
            value = LocalDateTime.now(WIB)
            delay(1000)
        }
    }

    val gpsReady by produceState(initialValue = ServiceLocator.location.isLocationEnabled()) {
        while (true) {
            value = ServiceLocator.location.isLocationEnabled()
            delay(3000)
        }
    }

    // Progres jam kerja standar 07:30 - 15:00 WIB (450 menit)
    val nowMinutes = now.hour * 60 + now.minute
    val workProgress = ((nowMinutes - 450).toFloat() / 450f).coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            // Header eksekutif + indikator GPS siap
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
                StatusPill(
                    text = if (gpsReady) "GPS Siap" else "GPS Mati",
                    tone = if (gpsReady) PillTone.Success else PillTone.Danger,
                )
            }

            Spacer(Modifier.height(20.dp))

            // Kartu Hero Jam, Tanggal, & Timeline Jam Kerja
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
                    Spacer(Modifier.height(12.dp))
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
                    LinearProgressIndicator(
                        progress = { workProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = accents.goldLight,
                        trackColor = Ivory.copy(alpha = 0.16f),
                        strokeCap = StrokeCap.Round,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Masuk 07:30",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ivory.copy(alpha = 0.70f),
                        )
                        Text(
                            text = "Pulang 15:00",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ivory.copy(alpha = 0.70f),
                        )
                    }
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
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Belum ada presensi hari ini. Ikuti 3 langkah berikut:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = accents.muted,
                            )
                            listOf(
                                "1" to "Pastikan GPS HP aktif dan berada di area kantor desa",
                                "2" to "Tekan tombol Pindai QR di bawah lalu arahkan ke QR kantor",
                                "3" to "Periksa jarak pada layar konfirmasi lalu simpan presensi",
                            ).forEach { (num, desc) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(accents.gold.copy(alpha = 0.20f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = num,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accents.goldDeep,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = accents.muted,
                                    )
                                }
                            }
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

            Spacer(Modifier.height(12.dp))
        }

        // Sticky CTA di area ibu jari (thumb zone)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, accents.hairline.copy(alpha = 0.7f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
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
        }
    }
}

private fun greeting(hour: Int): String = when (hour) {
    in 4..10 -> "Selamat pagi"
    in 11..14 -> "Selamat siang"
    in 15..17 -> "Selamat sore"
    else -> "Selamat malam"
}
