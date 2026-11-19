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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import id.desa.presensi.ui.UiState
import id.desa.presensi.ui.theme.BrandHeader
import id.desa.presensi.ui.theme.GoldRule
import id.desa.presensi.ui.theme.LedgerCard
import id.desa.presensi.ui.theme.LocalAccents
import id.desa.presensi.ui.theme.LuxuryTextField
import id.desa.presensi.ui.theme.NoticeBanner
import id.desa.presensi.ui.theme.PrimaryButton
import id.desa.presensi.ui.theme.SecondaryOutlineButton
import id.desa.presensi.ui.theme.SectionLabel

@Composable
fun LoginScreen(
    state: UiState,
    onLogin: (nipd: String, pin: String) -> Unit,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var nipd by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    val accents = LocalAccents.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Spacer(Modifier.height(12.dp))
            BrandHeader(
                eyebrow = "Pemerintahan Desa",
                title = "Presensi Desa",
                subtitle = "Sistem kehadiran perangkat desa yang akurat, aman, dan transparan.",
            )
            Spacer(Modifier.height(16.dp))
            GoldRule(width = 56)
            Spacer(Modifier.height(28.dp))

            LedgerCard(highlighted = true) {
                Column {
                    SectionLabel("Masuk ke akun")
                    Spacer(Modifier.height(14.dp))

                    LuxuryTextField(
                        value = nipd,
                        onValueChange = { if (it.length <= 24) nipd = it },
                        label = "NIPD",
                        placeholder = "Masukkan nomor induk perangkat",
                        enabled = !state.busy,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    Spacer(Modifier.height(14.dp))

                    LuxuryTextField(
                        value = pin,
                        onValueChange = { value ->
                            if (value.length <= 6 && value.all { it.isDigit() }) pin = value
                        },
                        label = "PIN 6 Digit",
                        placeholder = "••••••",
                        enabled = !state.busy,
                        visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Text(
                                text = if (showPin) "Tutup" else "Lihat",
                                style = MaterialTheme.typography.labelSmall,
                                color = accents.goldDeep,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { showPin = !showPin }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))

                    // Indikator progres 6 digit PIN
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(6) { index ->
                            val filled = index < pin.length
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (filled) accents.gold else accents.hairline,
                                    ),
                            )
                        }
                    }

                    if (state.error != null) {
                        Spacer(Modifier.height(16.dp))
                        NoticeBanner(message = state.error, isError = true)
                    }

                    Spacer(Modifier.height(22.dp))
                    PrimaryButton(
                        text = "Masuk Sekarang",
                        loading = state.busy,
                        enabled = nipd.isNotBlank() && pin.length == 6,
                        onClick = { onLogin(nipd, pin) },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            SecondaryOutlineButton(
                text = "Aktivasi Perangkat Baru",
                onClick = onActivate,
                enabled = !state.busy,
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(accents.surfaceElevated)
                .border(1.dp, accents.hairline, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accents.gold),
            )
            Text(
                text = "Satu akun terikat pada satu perangkat resmi. Hubungi Pengelola Desa bila Anda berganti HP.",
                style = MaterialTheme.typography.bodySmall,
                color = accents.muted,
            )
        }
    }
}
