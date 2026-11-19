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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.desa.presensi.ui.UiState
import id.desa.presensi.ui.theme.BrandHeader
import id.desa.presensi.ui.theme.GoldRule
import id.desa.presensi.ui.theme.LedgerCard
import id.desa.presensi.ui.theme.LocalAccents
import id.desa.presensi.ui.theme.LuxuryTextField
import id.desa.presensi.ui.theme.NoticeBanner
import id.desa.presensi.ui.theme.PillTone
import id.desa.presensi.ui.theme.PrimaryButton
import id.desa.presensi.ui.theme.SecondaryOutlineButton
import id.desa.presensi.ui.theme.SectionLabel
import id.desa.presensi.ui.theme.StatusPill

@Composable
fun ActivateScreen(
    state: UiState,
    onActivate: (nipd: String, code: String, pin: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var nipd by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    val accents = LocalAccents.current
    val pinMismatch = pinConfirm.length == 6 && pinConfirm != pin

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 24.dp),
    ) {
        BrandHeader(
            eyebrow = "Registrasi Perangkat",
            title = "Aktivasi HP",
            subtitle = "Cukup sekali. Setelah aktif, akun terikat aman pada perangkat ini.",
        )
        Spacer(Modifier.height(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GoldRule(width = 44)
            StatusPill(text = "Android Keystore", tone = PillTone.Success)
        }
        Spacer(Modifier.height(22.dp))

        LedgerCard(highlighted = true) {
            Column {
                SectionLabel("Lengkapi data aktivasi")
                Spacer(Modifier.height(14.dp))

                LuxuryTextField(
                    value = nipd,
                    onValueChange = { if (it.length <= 24) nipd = it },
                    label = "NIPD",
                    placeholder = "Nomor induk perangkat desa",
                    enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.height(12.dp))

                LuxuryTextField(
                    value = code,
                    onValueChange = { v -> if (v.length <= 8 && v.all { it.isDigit() }) code = v },
                    label = "Kode Aktivasi",
                    placeholder = "Kode 6 digit dari Pengelola Desa",
                    enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Kode dari Pengelola Desa berlaku selama 7 hari.",
                    style = MaterialTheme.typography.bodySmall,
                    color = accents.muted,
                )
                Spacer(Modifier.height(14.dp))

                LuxuryTextField(
                    value = pin,
                    onValueChange = { v -> if (v.length <= 6 && v.all { it.isDigit() }) pin = v },
                    label = "PIN Baru (6 Digit)",
                    placeholder = "••••••",
                    enabled = !state.busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.height(12.dp))

                LuxuryTextField(
                    value = pinConfirm,
                    onValueChange = { v -> if (v.length <= 6 && v.all { it.isDigit() }) pinConfirm = v },
                    label = "Ulangi PIN",
                    placeholder = "••••••",
                    isError = pinMismatch,
                    enabled = !state.busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                )
                if (pinMismatch) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Konfirmasi PIN belum cocok.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        if (state.error != null) {
            Spacer(Modifier.height(16.dp))
            NoticeBanner(message = state.error, isError = true)
        }

        Spacer(Modifier.height(22.dp))
        PrimaryButton(
            text = "Aktifkan Perangkat Ini",
            loading = state.busy,
            enabled = nipd.isNotBlank() && code.length >= 4 && pin.length == 6 && pin == pinConfirm,
            onClick = { onActivate(nipd, code, pin) },
        )

        Spacer(Modifier.height(10.dp))
        SecondaryOutlineButton(
            text = "Kembali ke Halaman Masuk",
            onClick = onBack,
            enabled = !state.busy,
        )

        Spacer(Modifier.height(20.dp))
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
                    .background(accents.success),
            )
            Text(
                text = "Kunci kriptografi dibuat di dalam Android Keystore dan tidak pernah meninggalkan perangkat Anda.",
                style = MaterialTheme.typography.bodySmall,
                color = accents.muted,
            )
        }
    }
}
