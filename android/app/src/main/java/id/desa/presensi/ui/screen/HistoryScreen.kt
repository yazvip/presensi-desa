package id.desa.presensi.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.desa.presensi.ui.UiState
import id.desa.presensi.ui.statusLabel
import id.desa.presensi.ui.theme.HeroEmeraldCard
import id.desa.presensi.ui.theme.Ivory
import id.desa.presensi.ui.theme.LedgerCard
import id.desa.presensi.ui.theme.LocalAccents
import id.desa.presensi.ui.theme.PillTone
import id.desa.presensi.ui.theme.SecondaryOutlineButton
import id.desa.presensi.ui.theme.SectionLabel
import id.desa.presensi.ui.theme.StatusPill
import id.desa.presensi.ui.timeLabel

private enum class HistoryFilter(val label: String) {
    ALL("Semua"),
    MASUK("Masuk"),
    PULANG("Pulang"),
    ON_TIME("Tepat Waktu"),
    LATE("Terlambat"),
}

@Composable
fun HistoryScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalAccents.current
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }

    LaunchedEffect(Unit) { onRefresh() }

    val total = state.history.size
    val onTime = state.history.count { it.status == "tepat_waktu" }
    val onTimePct = if (total > 0) (onTime * 100) / total else 0

    val filtered = remember(state.history, filter) {
        when (filter) {
            HistoryFilter.ALL -> state.history
            HistoryFilter.MASUK -> state.history.filter { it.type == "masuk" }
            HistoryFilter.PULANG -> state.history.filter { it.type == "pulang" }
            HistoryFilter.ON_TIME -> state.history.filter { it.status == "tepat_waktu" }
            HistoryFilter.LATE -> state.history.filter { it.status != "tepat_waktu" }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                SectionLabel("Catatan Kehadiran")
                Text("Riwayat Saya", style = MaterialTheme.typography.headlineMedium)
            }
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, accents.hairline),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = accents.muted,
                ),
            ) {
                Text("Kembali", style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Ringkasan metrik kehadiran + persentase ketepatan waktu
        HeroEmeraldCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricItem(label = "TOTAL SESI", value = total.toString(), color = Ivory)
                MetricItem(label = "TEPAT WAKTU", value = onTime.toString(), color = accents.goldLight)
                MetricItem(label = "DISIPLIN", value = "$onTimePct%", color = Ivory)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Chip filter riwayat
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(HistoryFilter.entries) { item ->
                val selected = filter == item
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) accents.gold else accents.hairline,
                            shape = RoundedCornerShape(20.dp),
                        )
                        .clickable { filter = item }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else accents.muted,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (filtered.isEmpty()) {
            LedgerCard(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.history.isEmpty()) {
                        "Belum ada riwayat kehadiran. Catatan akan muncul setelah absen pertama Anda."
                    } else {
                        "Tidak ada catatan untuk filter \"${filter.label}\"."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = accents.muted,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered) { row ->
                    LedgerCard(padding = PaddingValues(16.dp)) {
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
                                        .size(40.dp)
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

        Spacer(Modifier.height(14.dp))
        SecondaryOutlineButton(
            text = "Muat Ulang Riwayat",
            onClick = onRefresh,
        )
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    color: Color,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Ivory.copy(alpha = 0.72f),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.displayMedium,
            color = color,
        )
    }
}
