package id.desa.presensi.qr

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/** Kamera belakang + ML Kit, mendukung kontrol senter (torch) dan hanya membaca QR. */
@Composable
fun QrScannerView(
    modifier: Modifier = Modifier,
    torchEnabled: Boolean = false,
    onHasFlashChanged: (Boolean) -> Unit = {},
    onQrDetected: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanner = remember { BarcodeScanning.getClient(options) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    // Cegah callback berulang: frame kamera datang ~30x per detik.
    val handled = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    LaunchedEffect(boundCamera, torchEnabled) {
        val cam = boundCamera ?: return@LaunchedEffect
        if (cam.cameraInfo.hasFlashUnit()) {
            cam.cameraControl.enableTorch(torchEnabled)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            boundCamera?.cameraControl?.enableTorch(false)
            scanner.close()
            executor.shutdown()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(executor) { proxy -> analyze(proxy, scanner, handled, onQrDetected) } }

                    runCatching {
                        provider.unbindAll()
                        val camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                        boundCamera = camera
                        onHasFlashChanged(camera.cameraInfo.hasFlashUnit())
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
        )
    }

    LaunchedEffect(Unit) { handled.set(false) }
}

private val options: BarcodeScannerOptions =
    BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()

private fun analyze(
    proxy: ImageProxy,
    scanner: BarcodeScanner,
    handled: java.util.concurrent.atomic.AtomicBoolean,
    onQrDetected: (String) -> Unit,
) {
    val media = proxy.image
    if (media == null) {
        proxy.close()
        return
    }
    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)

    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            val raw = barcodes.firstOrNull { it.rawValue != null }?.rawValue
            if (raw != null && handled.compareAndSet(false, true)) onQrDetected(raw)
        }
        .addOnCompleteListener {
            proxy.close()
        }
}
