package id.desa.presensi.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.desa.presensi.ui.UiState
import id.desa.presensi.ui.theme.BrandHeader
import id.desa.presensi.ui.theme.LedgerCard
import id.desa.presensi.ui.theme.LocalAccents
import id.desa.presensi.ui.theme.NoticeBanner
import id.desa.presensi.ui.theme.PrimaryButton
import id.desa.presensi.ui.theme.SectionLabel

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        BrandHeader(
            title = "Aktivasi Perangkat",
            subtitle = "Sekali saja. Setelah ini akun terikat permanen pada HP ini.",
        )
        Spacer(Modifier.height(24.dp))

        LedgerCard {
            Column {
                SectionLabel("Data aktivasi")
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = nipd,
                    onValueChange = { if (it.length <= 24) nipd = it },
                    label = { Text("NIPD") },
                    singleLine = true,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) code = it },
                    label = { Text("Kode aktivasi") },
                    singleLine = true,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Kode 6 digit dari Pengelola Desa, berlaku 7 hari.",
                    style = MaterialTheme.typography.bodySmall,
                    color = accents.muted,
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                    label = { Text("PIN baru (6 digit)") },
                    singleLine = true,
                    enabled = !state.busy,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = pinConfirm,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pinConfirm = it },
                    label = { Text("Ulangi PIN") },
                    singleLine = true,
                    isError = pinConfirm.length == 6 && pinConfirm != pin,
                    enabled = !state.busy,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                )
            }
        }

        if (state.error != null) {
            Spacer(Modifier.height(16.dp))
            NoticeBanner(message = state.error, isError = true)
        }

        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = "Aktifkan HP ini",
            loading = state.busy,
            enabled = nipd.isNotBlank() && code.length >= 4 && pin.length == 6 && pin == pinConfirm,
            onClick = { onActivate(nipd, code, pin) },
        )

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Kembali ke halaman masuk")
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "Kunci perangkat dibuat di dalam Android Keystore dan tidak pernah keluar dari HP ini.",
            style = MaterialTheme.typography.bodySmall,
            color = accents.muted,
        )
    }
}
