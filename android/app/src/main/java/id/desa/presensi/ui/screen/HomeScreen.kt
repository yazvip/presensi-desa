package id.desa.presensi.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.desa.presensi.ui.UiState
import id.desa.presensi.ui.statusLabel
import id.desa.presensi.ui.theme.GoldRule
import id.desa.presensi.ui.theme.KeyValueRow
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
import java.time.LocalTime
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
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalAccents.current
    val session = state.session

    val now by produceState(initialValue = LocalTime.now(WIB)) {
        while (true) {
            value = LocalTime.now(WIB)
            delay(1000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Monogram(size = 44)
                Spacer(Modifier.width(14.dp))
                Column {
                    SectionLabel(greeting(now.hour))
                    Text(
                        text = session?.nama ?: "Petugas Desa",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
            TextButton(onClick = onSignOut) { Text("Keluar") }
        }

        Spacer(Modifier.height(28.dp))

        LedgerCard {
            Column {
                Text(
                    text = now.format(clockFormatter),
                    style = MaterialTheme.typography.displayLarge,
                    color = accents.emerald,
                )
                Text(
                    text = now.format(dateFormatter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = accents.muted,
                )
                Spacer(Modifier.height(16.dp))
                GoldRule(width = 56)
            }
        }

        Spacer(Modifier.height(16.dp))

        LedgerCard {
            Column {
                SectionLabel("Status hari ini")
                Spacer(Modifier.height(12.dp))
                if (state.today.isEmpty()) {
                    Text(
                        text = "Belum ada catatan. Absen masuk lewat QR di kantor desa.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    state.today.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = if (row.type == "masuk") "Masuk" else "Pulang",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "${row.timeLabel()} · ${row.officeName} · ${row.distanceM} m",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accents.muted,
                                )
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
            Spacer(Modifier.height(16.dp))
            NoticeBanner(message = state.error, isError = true)
        }
        if (state.message != null) {
            Spacer(Modifier.height(16.dp))
            NoticeBanner(message = state.message, isError = false)
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = when {
                state.doneForToday -> "Absensi hari ini selesai"
                state.hasCheckedIn -> "Absen Pulang"
                else -> "Absen Masuk"
            },
            enabled = !state.doneForToday,
            loading = state.busy,
            onClick = onScan,
        )

        Spacer(Modifier.height(12.dp))
        LedgerCard {
            Column {
                KeyValueRow("Jabatan", session?.jabatan ?: "-")
                KeyValueRow("NIPD", session?.nipd ?: "-")
                KeyValueRow("Batas HP", "${session?.deviceLimit ?: 1} perangkat")
                KeyValueRow("Titik absen", "QR kantor + radius 50 m")
            }
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) {
            Text("Lihat riwayat kehadiran", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun greeting(hour: Int): String = when (hour) {
    in 4..10 -> "Selamat pagi"
    in 11..14 -> "Selamat siang"
    in 15..17 -> "Selamat sore"
    else -> "Selamat malam"
}
