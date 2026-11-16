package id.desa.presensi.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.desa.presensi.ui.UiState
import id.desa.presensi.ui.statusLabel
import id.desa.presensi.ui.theme.LedgerCard
import id.desa.presensi.ui.theme.LocalAccents
import id.desa.presensi.ui.theme.PillTone
import id.desa.presensi.ui.theme.SectionLabel
import id.desa.presensi.ui.theme.StatusPill
import id.desa.presensi.ui.timeLabel

@Composable
fun HistoryScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalAccents.current

    LaunchedEffect(Unit) { onRefresh() }

    Column(modifier = modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                SectionLabel("Riwayat")
                Text("Kehadiran saya", style = MaterialTheme.typography.headlineMedium)
            }
            TextButton(onClick = onBack) { Text("Kembali") }
        }

        Spacer(Modifier.height(16.dp))

        if (state.history.isEmpty()) {
            LedgerCard {
                Text(
                    text = "Belum ada riwayat. Catatan muncul setelah absen pertama.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.history) { row ->
                    LedgerCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(row.workDate, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${if (row.type == "masuk") "Masuk" else "Pulang"} ${row.timeLabel()} · ${row.officeName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accents.muted,
                                )
                                Text(
                                    text = "Jarak ${row.distanceM} m · akurasi ${row.accM} m",
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

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = accents.hairline)
        TextButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Text("Muat ulang")
        }
    }
}
