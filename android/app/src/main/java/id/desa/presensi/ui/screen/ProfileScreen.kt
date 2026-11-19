package id.desa.presensi.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import id.desa.presensi.data.ThemeMode
import id.desa.presensi.ui.UiState
import id.desa.presensi.ui.theme.HeroEmeraldCard
import id.desa.presensi.ui.theme.Ivory
import id.desa.presensi.ui.theme.KeyValueRow
import id.desa.presensi.ui.theme.LedgerCard
import id.desa.presensi.ui.theme.LocalAccents
import id.desa.presensi.ui.theme.Monogram
import id.desa.presensi.ui.theme.PillTone
import id.desa.presensi.ui.theme.SectionLabel
import id.desa.presensi.ui.theme.StatusPill

@Composable
fun ProfileScreen(
    state: UiState,
    onSelectTheme: (ThemeMode) -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalAccents.current
    val session = state.session
    val context = LocalContext.current
    var confirmLogout by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onToggleReminder(true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        SectionLabel("Akun & Preferensi")
        Text("Profil Perangkat", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        HeroEmeraldCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Monogram(size = 56)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session?.nama ?: "Petugas Desa",
                        style = MaterialTheme.typography.titleLarge,
                        color = Ivory,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = session?.jabatan ?: "Perangkat Desa",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ivory.copy(alpha = 0.78f),
                    )
                    Spacer(Modifier.height(8.dp))
                    StatusPill(
                        text = "NIPD ${session?.nipd ?: "-"}",
                        tone = PillTone.GoldDarkBg,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        LedgerCard {
            Column {
                SectionLabel("Informasi Keamanan")
                Spacer(Modifier.height(10.dp))
                KeyValueRow("Peran", session?.role ?: "-")
                HorizontalDivider(color = accents.hairline.copy(alpha = 0.6f))
                KeyValueRow("Batas Perangkat", "${session?.deviceLimit ?: 1} perangkat")
                HorizontalDivider(color = accents.hairline.copy(alpha = 0.6f))
                KeyValueRow("Validasi Presensi", "QR Kantor + Radius 50 m")
                HorizontalDivider(color = accents.hairline.copy(alpha = 0.6f))
                KeyValueRow("Kunci Perangkat", "Android Keystore Aktif")
            }
        }

        Spacer(Modifier.height(14.dp))

        LedgerCard {
            Column {
                SectionLabel("Tampilan Aplikasi")
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        ThemeMode.SYSTEM to "Otomatis",
                        ThemeMode.LIGHT to "Terang",
                        ThemeMode.DARK to "Gelap",
                    ).forEach { (mode, label) ->
                        val selected = state.themeMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary else accents.surfaceElevated,
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) accents.gold else accents.hairline,
                                    shape = RoundedCornerShape(14.dp),
                                )
                                .clickable { onSelectTheme(mode) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else accents.muted,
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 14.dp),
                    color = accents.hairline.copy(alpha = 0.6f),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
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

        Spacer(Modifier.height(18.dp))
        OutlinedButton(
            onClick = { confirmLogout = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.06f),
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text("Keluar dari Akun", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(12.dp))
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Keluar dari Akun?") },
            text = { Text("Anda perlu memasukkan NIPD dan PIN kembali saat membuka aplikasi nanti.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    onSignOut()
                }) {
                    Text("Ya, Keluar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) {
                    Text("Batal")
                }
            },
        )
    }
}
