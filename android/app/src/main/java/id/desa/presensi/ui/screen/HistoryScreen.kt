package id.desa.presensi.ui.screen

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.desa.presensi.data.AttendanceDto
import id.desa.presensi.ui.UiState
import id.desa.presensi.ui.statusLabel
import id.desa.presensi.ui.theme.HeroEmeraldCard
import id.desa.presensi.ui.theme.Ivory
import id.desa.presensi.ui.theme.LedgerCard
import id.desa.presensi.ui.theme.LocalAccents
import id.desa.presensi.ui.theme.PillTone
import id.desa.presensi.ui.theme.SectionLabel
import id.desa.presensi.ui.theme.StatusPill
import id.desa.presensi.ui.timeLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class HistoryFilter(val label: String) {
    ALL("Semua"),
    MASUK("Masuk"),
    PULANG("Pulang"),
    ON_TIME("Tepat Waktu"),
    LATE("Terlambat"),
}

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("id-ID"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: UiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalAccents.current
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    var refreshing by remember { mutableStateOf(false) }

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

    val grouped = remember(filtered) {
        filtered.groupBy { monthLabel(it.workDate) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        SectionLabel("Catatan Kehadiran")
        Text("Riwayat Saya", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(16.dp))

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

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                onRefresh()
                scope.launch {
                    delay(700)
                    refreshing = false
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            if (filtered.isEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        LedgerCard {
                            Text(
                                text = if (state.history.isEmpty()) {
                                    "Belum ada riwayat kehadiran. Tarik ke bawah untuk memuat ulang."
                                } else {
                                    "Tidak ada catatan untuk filter \"${filter.label}\"."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = accents.muted,
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    grouped.forEach { (month, rows) ->
                        item(key = "hdr-$month") {
                            SectionLabel(
                                text = month,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                            )
                        }
                        items(rows, key = { it.id }) { row ->
                            HistoryRowCard(row = row)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRowCard(row: AttendanceDto) {
    val accents = LocalAccents.current
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

private fun monthLabel(workDate: String): String {
    val d = runCatching { LocalDate.parse(workDate.take(10)) }.getOrNull() ?: return "Lainnya"
    return d.format(monthFormatter)
}
