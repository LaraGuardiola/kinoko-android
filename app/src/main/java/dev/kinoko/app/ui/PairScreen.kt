package dev.kinoko.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import dev.kinoko.app.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.net.URLEncoder
import java.util.concurrent.Executors

@Composable
fun PairScreen(busy: Boolean, error: String?, onLink: (String) -> Unit) {
    var scanning by rememberSaveable { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var url by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> scanning = granted }

    if (scanning) {
        Scanner(onCode = { scanning = false; onLink(it) }, onCancel = { scanning = false })
        return
    }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(150.dp))
        Text("kinoko", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Kinoko.cream)
        Text(
            "Your agents, from anywhere. On your computer run\nkinoko serve\nand scan the code it shows.",
            style = MaterialTheme.typography.bodyMedium,
            color = Kinoko.muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scanning = true
                else camera.launch(Manifest.permission.CAMERA)
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Kinoko.softRed),
        ) {
            Icon(Icons.Outlined.QrCodeScanner, null)
            Text("  Scan the code", fontSize = 16.sp)
        }
        OutlinedButton(
            onClick = { clipboard.getText()?.text?.let(onLink) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Kinoko.line),
        ) {
            Icon(Icons.Outlined.ContentPaste, null, tint = Kinoko.cream)
            Text("  Paste a pairing link", color = Kinoko.cream)
        }
        TextButton(onClick = { manual = !manual }) { Text(if (manual) "Hide" else "Enter the address and code", color = Kinoko.muted) }
        AnimatedVisibility(manual) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(url, { url = it.trim() }, label = { Text("Address") }, placeholder = { Text("https://machine.tailnet.ts.net:8443") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(code, { code = it.trim() }, label = { Text("Code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { onLink("kinoko://pair?u=${URLEncoder.encode(url, "UTF-8")}&c=$code") },
                    enabled = !busy && url.isNotBlank() && code.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Pair") }
            }
        }
        if (busy) CircularProgressIndicator(color = Kinoko.cream, modifier = Modifier.size(28.dp))
        if (error != null) Text(error, color = Kinoko.softRed, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

/** The camera, looking for a kinoko pairing QR code. */
@Composable
private fun Scanner(onCode: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var done by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    Box(Modifier.fillMaxSize().background(Kinoko.night).safeDrawingPadding(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                val view = PreviewView(ctx)
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                    val reader = MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)) }
                    val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analysis.setAnalyzer(executor) { image ->
                        try {
                            val plane = image.planes[0]
                            val bytes = ByteArray(plane.buffer.remaining()).also { plane.buffer.get(it) }
                            val source = PlanarYUVLuminanceSource(bytes, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
                            val text = runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text }.getOrNull()
                            if (text != null && text.startsWith("kinoko://") && !done) {
                                done = true
                                ContextCompat.getMainExecutor(context).execute { onCode(text) }
                            }
                        } finally {
                            image.close()
                        }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }, ContextCompat.getMainExecutor(ctx))
                view
            },
            modifier = Modifier.fillMaxWidth(0.86f).aspectRatio(1f).clip(RoundedCornerShape(24.dp)).border(2.dp, Kinoko.cream, RoundedCornerShape(24.dp)),
        )
        Column(Modifier.align(Alignment.BottomCenter).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Point at the code from kinoko serve", color = Kinoko.text)
            TextButton(onClick = onCancel) { Text("Cancel", color = Kinoko.muted) }
        }
    }
}
