package org.assistix.proto.nativeapp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoSessionStore

internal fun parseProtoLinkQr(text: String): Pair<String, String>? {
    val raw = text.trim()
    if (raw.isEmpty()) return null
    val uri =
        try {
            Uri.parse(if (raw.contains("://")) raw else "https://proto.su/app/?$raw")
        } catch (_: Exception) {
            return null
        }
    val pairId = uri.getQueryParameter("p")?.trim().orEmpty()
    val secret = uri.getQueryParameter("s")?.trim().orEmpty()
    if (pairId.isEmpty() || secret.isEmpty()) return null
    return pairId to secret
}

@Composable
fun LinkQrScannerScreen(
    session: ProtoSessionStore,
    api: ProtoApi,
    onBack: () -> Unit,
    onLinked: () -> Unit,
    onRawQr: ((String) -> Boolean)? = null,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(UiStrings.scanQrInstruction) }
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    var qrDetected by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val handled = remember { AtomicBoolean(false) }

    LaunchedEffect(torchEnabled, camera) {
        runCatching { camera?.cameraControl?.enableTorch(torchEnabled) }
    }

    fun approve(pairId: String, secret: String) {
        if (!handled.compareAndSet(false, true)) return
        scope.launch {
            val t = session.token() ?: return@launch
            busy = true
            status = UiStrings.scanQrFound
            qrDetected = true
            val (ok, err) = withContext(Dispatchers.IO) { api.approveDeviceLink(t, pairId, secret) }
            busy = false
            if (ok) {
                Toast.makeText(ctx, UiStrings.linkWebApproved, Toast.LENGTH_SHORT).show()
                onLinked()
            } else {
                handled.set(false)
                status = err ?: UiStrings.linkQrInvalid
                qrDetected = false
            }
        }
    }

    val permLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCamera = granted
            if (!granted) permissionDenied = true
        }

    DisposableEffect(Unit) {
        if (!hasCamera) {
            permLauncher.launch(Manifest.permission.CAMERA)
        }
        onDispose {
            runCatching { camera?.cameraControl?.enableTorch(false) }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            hasCamera && !permissionDenied -> {
                QrCameraPreview(
                    onCameraReady = { camera = it },
                    onQrSeen = { seen -> qrDetected = seen },
                    onQrText = { text ->
                        if (onRawQr != null && onRawQr(text)) return@QrCameraPreview
                        val parsed = parseProtoLinkQr(text) ?: return@QrCameraPreview
                        approve(parsed.first, parsed.second)
                    },
                )
                ProtoQrScannerLayout(
                    onClose = onBack,
                    instruction = status,
                    detected = qrDetected || busy,
                    torchEnabled = torchEnabled,
                    onTorchToggle = { torchEnabled = !torchEnabled },
                )
                if (busy) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                    }
                }
                if (!busy && status == UiStrings.linkQrInvalid) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 88.dp),
                    ) {
                        ProtoGhostButton(
                            UiStrings.scanAgain,
                            {
                                handled.set(false)
                                status = UiStrings.scanQrInstruction
                                qrDetected = false
                            },
                            Modifier.padding(horizontal = 48.dp),
                        )
                    }
                }
            }
            permissionDenied -> {
                ProtoQrScannerLayout(
                    onClose = onBack,
                    instruction = UiStrings.cameraRequiredForQr,
                    detected = false,
                    torchEnabled = false,
                    onTorchToggle = {},
                )
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(1f))
                    ProtoPrimaryButton(
                        UiStrings.scanAgain,
                        {
                            permissionDenied = false
                            permLauncher.launch(Manifest.permission.CAMERA)
                        },
                        Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.weight(0.35f))
                }
            }
            else -> {
                ProtoQrScannerLayout(
                    onClose = onBack,
                    instruction = UiStrings.scanQrInstruction,
                    detected = false,
                    torchEnabled = false,
                    onTorchToggle = {},
                )
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun QrCameraPreview(
    onCameraReady: (Camera?) -> Unit,
    onQrSeen: (Boolean) -> Unit,
    onQrText: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner =
        remember {
            val opts =
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
            BarcodeScanning.getClient(opts)
        }
    val consumed = remember { AtomicBoolean(false) }
    val cameraBound = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            onCameraReady(null)
            executor.shutdown()
            scanner.close()
        }
    }

    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { previewView ->
            if (!cameraBound.compareAndSet(false, true)) return@AndroidView
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    val analysis =
                        ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                    analysis.setAnalyzer(executor) { proxy ->
                        if (consumed.get()) {
                            proxy.close()
                            return@setAnalyzer
                        }
                        processQrFrame(proxy, scanner, onQrSeen) { raw ->
                            if (consumed.compareAndSet(false, true)) {
                                onQrText(raw)
                            }
                        }
                    }
                    try {
                        cameraProvider.unbindAll()
                        val bound =
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                        onCameraReady(bound)
                    } catch (_: Exception) {
                        cameraBound.set(false)
                        onCameraReady(null)
                    }
                },
                ContextCompat.getMainExecutor(ctx),
            )
        },
    )
}

@OptIn(ExperimentalGetImage::class)
private fun processQrFrame(
    proxy: androidx.camera.core.ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onQrSeen: (Boolean) -> Unit,
    onQrText: (String) -> Unit,
) {
    val media = proxy.image
    if (media == null) {
        proxy.close()
        return
    }
    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
    scanner
        .process(image)
        .addOnSuccessListener { codes ->
            val qr = codes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE && !it.rawValue.isNullOrBlank() }
            if (qr != null) {
                onQrSeen(true)
                onQrText(qr.rawValue!!)
            } else {
                onQrSeen(false)
            }
        }
        .addOnFailureListener { onQrSeen(false) }
        .addOnCompleteListener { proxy.close() }
}
