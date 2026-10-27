package com.nobodybuilds.listens.presentation.taste

import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import timber.log.Timber

/**
 * Camera preview that looks for Nobody Listens taste QR codes (bundled ML Kit model, works offline).
 * The camera is bound to the screen's lifecycle and fully released when this leaves composition.
 */
@Composable
fun TasteQrScanner(
    onCode: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnCode = rememberUpdatedState(onCode)
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner) {
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        val scanner: BarcodeScanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        )
        val delivered = AtomicBoolean(false)
        val busy = AtomicBoolean(false)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        val mainExecutor = ContextCompat.getMainExecutor(context)

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(executor) { proxy ->
            if (delivered.get() || !busy.compareAndSet(false, true)) {
                proxy.close()
                return@setAnalyzer
            }
            analyze(proxy, scanner) { value ->
                if (value != null && !delivered.get()) {
                    mainExecutor.execute {
                        if (!disposed && !delivered.get()) {
                            // A wrong QR (not a taste code) keeps scanning; a taste code stops it.
                            if (latestOnCode.value(value)) delivered.set(true)
                        }
                    }
                }
                busy.set(false)
            }
        }

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (disposed) return@addListener
            runCatching {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }.onFailure { Timber.w(it, "Taste: camera bind failed") }
        }, mainExecutor)

        onDispose {
            disposed = true
            runCatching { provider?.unbindAll() }
            analysis.clearAnalyzer()
            runCatching { scanner.close() }
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

@OptIn(ExperimentalGetImage::class)
private fun analyze(proxy: ImageProxy, scanner: BarcodeScanner, done: (String?) -> Unit) {
    val media = proxy.image
    if (media == null) {
        proxy.close()
        done(null)
        return
    }
    val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
    scanner.process(input)
        .addOnSuccessListener { codes ->
            done(codes.firstNotNullOfOrNull { c -> c.rawValue?.takeIf { it.contains("LMT1:") } }
                ?: codes.firstNotNullOfOrNull { it.rawValue })
        }
        .addOnFailureListener { done(null) }
        .addOnCompleteListener { proxy.close() }
}
