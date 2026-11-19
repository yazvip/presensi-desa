package id.desa.presensi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.desa.presensi.data.ThemeMode
import id.desa.presensi.ui.MainViewModel
import id.desa.presensi.ui.screen.ActivateScreen
import id.desa.presensi.ui.screen.HistoryScreen
import id.desa.presensi.ui.screen.HomeScreen
import id.desa.presensi.ui.screen.LoginScreen
import id.desa.presensi.ui.screen.ScannerScreen
import id.desa.presensi.ui.theme.LocalAccents
import id.desa.presensi.ui.theme.Monogram
import id.desa.presensi.ui.theme.PresensiTheme

private enum class Route { Login, Activate, Home, Scanner, History }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            val darkTheme = when (state.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            PresensiTheme(darkTheme = darkTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .safeDrawingPadding(),
                ) {
                    PresensiRoot(vm = vm)
                }
            }
        }
    }
}

@Composable
private fun PresensiRoot(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf(Route.Login) }

    // Sesi tersimpan otomatis membuka layar utama.
    LaunchedEffect(state.booting, state.session) {
        if (!state.booting && state.session != null && route == Route.Login) {
            route = Route.Home
        }
    }

    if (state.booting) {
        val accents = LocalAccents.current
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Monogram(size = 76)
            Spacer(Modifier.height(20.dp))
            Text(
                text = "PRESENSI DESA",
                style = MaterialTheme.typography.labelSmall,
                color = accents.gold,
            )
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        return
    }

    AnimatedContent(
        targetState = route,
        transitionSpec = {
            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
        },
        label = "route",
    ) { current ->
        when (current) {
            Route.Login -> LoginScreen(
                state = state,
                onLogin = { nipd, pin ->
                    vm.login(nipd, pin) { route = Route.Home }
                },
                onActivate = { vm.clearMessages(); route = Route.Activate },
            )

            Route.Activate -> ActivateScreen(
                state = state,
                onActivate = { nipd, code, pin ->
                    vm.activate(nipd, code, pin, null) { route = Route.Home }
                },
                onBack = { vm.clearMessages(); route = Route.Login },
            )

            Route.Home -> HomeScreen(
                state = state,
                onScan = {
                    vm.clearMessages()
                    vm.startScanning()
                    route = Route.Scanner
                },
                onHistory = { route = Route.History },
                onToggleTheme = { vm.cycleTheme() },
                onToggleReminder = { enabled -> vm.setReminderEnabled(enabled) },
                onSignOut = {
                    vm.signOut()
                    route = Route.Login
                },
            )

            Route.Scanner -> ScannerScreen(
                state = state,
                onQrDetected = { qr -> vm.onQrScanned(qr) },
                onConfirm = { vm.confirmAttendance() },
                onDone = {
                    vm.stopScanning()
                    vm.clearMessages()
                    route = Route.Home
                },
                onCancel = {
                    vm.stopScanning()
                    vm.clearMessages()
                    route = Route.Home
                },
            )

            Route.History -> HistoryScreen(
                state = state,
                onRefresh = { vm.refreshHistory() },
                onBack = { route = Route.Home },
            )
        }
    }
}
