package id.desa.presensi.ui.screen

import androidx.compose.foundation.layout.Arrangement
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
import id.desa.presensi.ui.theme.GoldRule
import id.desa.presensi.ui.theme.LocalAccents
import id.desa.presensi.ui.theme.NoticeBanner
import id.desa.presensi.ui.theme.PrimaryButton

@Composable
fun LoginScreen(
    state: UiState,
    onLogin: (nipd: String, pin: String) -> Unit,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var nipd by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    val accents = LocalAccents.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BrandHeader(
            title = "Presensi Desa",
            subtitle = "Kehadiran perangkat desa, tercatat rapi dan transparan.",
        )
        Spacer(Modifier.height(18.dp))
        GoldRule()
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = nipd,
            onValueChange = { if (it.length <= 24) nipd = it },
            label = { Text("NIPD") },
            singleLine = true,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { value -> if (value.length <= 6 && value.all(Char::isDigit)) pin = value },
            label = { Text("PIN 6 digit") },
            singleLine = true,
            enabled = !state.busy,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
        )

        if (state.error != null) {
            Spacer(Modifier.height(16.dp))
            NoticeBanner(message = state.error, isError = true)
        }

        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = "Masuk",
            loading = state.busy,
            enabled = nipd.isNotBlank() && pin.length == 6,
            onClick = { onLogin(nipd, pin) },
        )

        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onActivate, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text("HP ini belum terdaftar — aktivasi perangkat", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(28.dp))
        Text(
            text = "Satu akun terikat pada satu HP. Minta Pengelola Desa menghapus key device bila Anda berganti perangkat.",
            style = MaterialTheme.typography.bodySmall,
            color = accents.muted,
        )
    }
}
