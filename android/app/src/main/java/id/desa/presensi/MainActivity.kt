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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.desa.presensi.update.AppUpdater
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.desa.presensi.data.ThemeMode
import id.desa.presensi.ui.MainViewModel
import id.desa.presensi.ui.screen.ActivateScreen
import id.desa.presensi.ui.screen.HistoryScreen
import id.desa.presensi.ui.screen.HomeScreen
import id.desa.presensi.ui.screen.LoginScreen
import id.desa.presensi.ui.screen.ProfileScreen
import id.desa.presensi.ui.screen.ScannerScreen
import id.desa.presensi.ui.theme.LocalAccents
import id.desa.presensi.ui.theme.Monogram
import id.desa.presensi.ui.theme.PresensiTheme

private enum class Route { Login, Activate, Home, Scanner, History, Profile }

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

    val context = LocalContext.current
    val update = state.availableUpdate
    if (update != null) {
        AlertDialog(
            onDismissRequest = { vm.dismissUpdate() },
            title = { Text("Pembaruan Tersedia (${update.versionName})") },
            text = { Text(update.notes) },
            confirmButton = {
                TextButton(onClick = {
                    AppUpdater.downloadAndInstall(context, update)
                    vm.dismissUpdate()
                }) {
                    Text("Update Sekarang", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissUpdate() }) {
                    Text("Lewati")
                }
            },
        )
    }

    val showBottomBar = route == Route.Home || route == Route.History || route == Route.Profile

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                LuxuryBottomBar(
                    current = route,
                    onSelect = { selected ->
                        vm.clearMessages()
                        route = selected
                    },
                )
            }
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = route,
            modifier = Modifier.padding(innerPadding),
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
                )

                Route.Scanner -> ScannerScreen(
                    state = state,
                    onQrDetected = { qr -> vm.onQrScanned(qr) },
                    onConfirm = { vm.confirmAttendance() },
                    onRetryScan = { vm.startScanning() },
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
                )

                Route.Profile -> ProfileScreen(
                    state = state,
                    onSelectTheme = { mode -> vm.setThemeMode(mode) },
                    onToggleReminder = { enabled -> vm.setReminderEnabled(enabled) },
                    onSignOut = {
                        vm.signOut()
                        route = Route.Login
                    },
                )
            }
        }
    }
}

@Composable
private fun LuxuryBottomBar(
    current: Route,
    onSelect: (Route) -> Unit,
) {
    val accents = LocalAccents.current
    val items = listOf(
        Route.Home to "Beranda",
        Route.History to "Riwayat",
        Route.Profile to "Profil",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, accents.hairline)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { (tab, label) ->
            val selected = current == tab
            val fg = if (selected) MaterialTheme.colorScheme.onPrimary else accents.muted
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else accents.surfaceElevated,
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) accents.gold else accents.hairline,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .clickable { onSelect(tab) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                TabIcon(route = tab, color = fg)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = fg,
                )
            }
        }
    }
}


@Composable
private fun TabIcon(route: Route, color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (route) {
            Route.Home -> {
                val p = Path().apply {
                    moveTo(w * 0.15f, h * 0.48f)
                    lineTo(w * 0.50f, h * 0.16f)
                    lineTo(w * 0.85f, h * 0.48f)
                    moveTo(w * 0.25f, h * 0.42f)
                    lineTo(w * 0.25f, h * 0.84f)
                    lineTo(w * 0.75f, h * 0.84f)
                    lineTo(w * 0.75f, h * 0.42f)
                }
                drawPath(p, color = color, style = stroke)
            }
            Route.History -> {
                drawCircle(color = color, radius = w * 0.38f, style = stroke)
                drawLine(color, Offset(w * 0.50f, h * 0.30f), Offset(w * 0.50f, h * 0.52f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.52f), Offset(w * 0.66f, h * 0.62f), stroke.width, StrokeCap.Round)
            }
            Route.Profile -> {
                drawCircle(color = color, radius = w * 0.19f, center = Offset(w * 0.50f, h * 0.33f), style = stroke)
                val arc = Path().apply {
                    moveTo(w * 0.20f, h * 0.84f)
                    quadraticTo(w * 0.50f, h * 0.58f, w * 0.80f, h * 0.84f)
                }
                drawPath(arc, color = color, style = stroke)
            }
            else -> {}
        }
    }
}
