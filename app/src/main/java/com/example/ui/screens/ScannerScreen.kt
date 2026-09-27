package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.model.ConfigData
import com.example.ui.MainViewModel
import com.example.ui.theme.ZeroBlueLight
import com.example.ui.theme.ZeroGreenSuccess
import com.example.util.QrCodeAnalyzer
import android.widget.Toast
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.data.network.DispatchResult
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(
    viewModel: MainViewModel,
    onScanSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    var cameraInstance by remember { mutableStateOf<Camera?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var showManualInputDialog by remember { mutableStateOf(false) }
    var manualJsonText by remember { mutableStateOf("") }
    var scannedSuccessConfig by remember { mutableStateOf<ConfigData?>(null) }
    var scanErrorMessage by remember { mutableStateOf<String?>(null) }
    var isSuccessDetected by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var detectedPayloadText by remember { mutableStateOf("") }

    val qrAnalyzer = remember {
        var analyzerInstance: QrCodeAnalyzer? = null
        val analyzer = QrCodeAnalyzer { rawScannedText ->
            val cleanText = rawScannedText.trim()
            if (!isVerifying && !isSuccessDetected) {
                Handler(Looper.getMainLooper()).post {
                    if (isVerifying || isSuccessDetected) return@post
                    detectedPayloadText = cleanText
                    isVerifying = true
                    analyzerInstance?.setScanningEnabled(false)

                    scope.launch {
                        val result = viewModel.connectAndHandshakePayload(cleanText)
                        isVerifying = false
                        when (result) {
                            is DispatchResult.Success -> {
                                isSuccessDetected = true
                                Toast.makeText(context, "Connected & Paired successfully!", Toast.LENGTH_SHORT).show()
                                kotlinx.coroutines.delay(750L)
                                onScanSuccess()
                            }
                            is DispatchResult.Failure -> {
                                val toastMsg = if (result.errorMessage.contains("Invalid Token", ignoreCase = true) || result.code in listOf(400, 401, 403, 404)) {
                                    "Invalid Token or Server Rejected Pairing"
                                } else {
                                    result.errorMessage
                                }
                                Toast.makeText(context, toastMsg, Toast.LENGTH_LONG).show()
                                scanErrorMessage = toastMsg
                                analyzerInstance?.setScanningEnabled(true)
                            }
                        }
                    }
                }
            }
        }
        analyzerInstance = analyzer
        analyzer
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("scanner_screen")
    ) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    it.setAnalyzer(cameraExecutor, qrAnalyzer)
                                }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            cameraProvider.unbindAll()
                            val cam = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                            cameraInstance = cam
                        } catch (e: Exception) {
                            Log.e("ScannerScreen", "Camera binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Scanning Overlay UI with Success State Feedback
            ScannerOverlay(
                modifier = Modifier.fillMaxSize(),
                isSuccess = isSuccessDetected,
                isVerifying = isVerifying,
                detectedPayload = detectedPayloadText
            )

            // Top Bar Controls (Torch & Manual Paste)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 40.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        cameraInstance?.let { cam ->
                            if (cam.cameraInfo.hasFlashUnit()) {
                                isTorchOn = !isTorchOn
                                cam.cameraControl.enableTorch(isTorchOn)
                            }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        
                        .background(Color.Black.copy(alpha = 0.6f))
                        .testTag("torch_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Torch Toggle",
                        tint = if (isTorchOn) Color.Yellow else Color.White
                    )
                }

                Button(
                    onClick = { showManualInputDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("paste_config_button")
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Paste JSON")
                }
            }

            // Bottom Instructions Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        shadowElevation = 2.dp,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_white_bg),
                            contentDescription = "Zero Pay Logo",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Scan Setup QR Code",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Align the QR code from your Zero Pay dashboard inside the frame above to configure this device.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            // Demo payload button for instant testing
                            val demoJson = """{"webhookUrl": "https://zeropay-dev.vercel.app/api/v1/webhook", "deviceToken": "dev-token-secret-12345", "deviceId": "android-samsung-a52-01"}"""
                            val result = viewModel.onQrScanned(demoJson)
                            if (result.isSuccess) {
                                onScanSuccess()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("use_sample_qr_button")
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Use Sample Zero Pay Config")
                    }
                }
            }

        } else {
            // Camera Permission Not Granted view
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = ZeroBlueLight
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera Permission Required",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The app needs camera access to scan your Zero Pay device setup QR code.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.testTag("request_camera_permission_button")
                ) {
                    Text("Allow Camera Access")
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = { showManualInputDialog = true }
                ) {
                    Text("Or enter configuration manually", color = ZeroBlueLight)
                }
            }
        }
    }

    // Success Dialog on Scan
    scannedSuccessConfig?.let { cfg ->
        AlertDialog(
            onDismissRequest = {
                scannedSuccessConfig = null
                onScanSuccess()
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = ZeroGreenSuccess)
                    Text("Connected to Zero Pay")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Device successfully configured and saved to secure storage!",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Device ID: ${cfg.deviceId}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            Text("Endpoint: ${cfg.webhookUrl}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scannedSuccessConfig = null
                        onScanSuccess()
                    },
                    modifier = Modifier.testTag("scan_success_confirm_button")
                ) {
                    Text("Go to Dashboard")
                }
            }
        )
    }

    // Error Dialog if QR is invalid
    scanErrorMessage?.let { errMsg ->
        AlertDialog(
            onDismissRequest = {
                scanErrorMessage = null
                qrAnalyzer.setScanningEnabled(true)
            },
            title = { Text("Invalid QR Code") },
            text = { Text(errMsg) },
            confirmButton = {
                Button(onClick = {
                    scanErrorMessage = null
                    qrAnalyzer.setScanningEnabled(true)
                }) {
                    Text("Scan Again")
                }
            }
        )
    }

    // Manual JSON Dialog
    if (showManualInputDialog) {
        AlertDialog(
            onDismissRequest = { showManualInputDialog = false },
            title = { Text("Enter Setup JSON") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste the JSON string from your Zero Pay setup screen:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = manualJsonText,
                        onValueChange = { manualJsonText = it },
                        placeholder = {
                            Text(
                                "{\"webhookUrl\": \"https://...\", \"deviceSecret\": \"...\", \"deviceId\": \"...\"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_json_input")
                    )
                }
            },
            confirmButton = {
                var isSubmittingManual by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        val text = manualJsonText.trim()
                        if (text.isBlank()) {
                            scanErrorMessage = "Please enter setup JSON"
                            return@Button
                        }
                        isSubmittingManual = true
                        scope.launch {
                            val result = viewModel.connectAndHandshakePayload(text)
                            isSubmittingManual = false
                            when (result) {
                                is DispatchResult.Success -> {
                                    Toast.makeText(context, "Connected & Paired successfully!", Toast.LENGTH_SHORT).show()
                                    showManualInputDialog = false
                                    onScanSuccess()
                                }
                                is DispatchResult.Failure -> {
                                    val toastMsg = if (result.errorMessage.contains("Invalid Token", ignoreCase = true) || result.code in listOf(400, 401, 403, 404)) {
                                        "Invalid Token or Server Rejected Pairing"
                                    } else {
                                        result.errorMessage
                                    }
                                    Toast.makeText(context, toastMsg, Toast.LENGTH_LONG).show()
                                    scanErrorMessage = toastMsg
                                }
                            }
                        }
                    },
                    enabled = !isSubmittingManual,
                    modifier = Modifier.testTag("submit_manual_json_button")
                ) {
                    if (isSubmittingManual) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verifying...")
                    } else {
                        Text("Save & Connect")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualInputDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ScannerOverlay(
    modifier: Modifier = Modifier,
    isSuccess: Boolean = false,
    isVerifying: Boolean = false,
    detectedPayload: String = ""
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan_laser")
    val laserPosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_pos"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Darkened surrounding background with square cutout in center
        Box(
            modifier = Modifier
                .size(280.dp)
                .border(
                    width = if (isSuccess) 3.dp else 2.dp,
                    color = if (isSuccess) Color(0xFF10B981) else if (isVerifying) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(24.dp)
                )
                .clip(RoundedCornerShape(24.dp))
                .background(if (isSuccess) Color(0xFF10B981).copy(alpha = 0.15f) else if (isVerifying) Color(0xFF38BDF8).copy(alpha = 0.12f) else Color.Transparent)
        ) {
            if (isVerifying) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = Color(0xFF38BDF8),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "VERIFYING PAIRING...",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Validating token with Zero Pay...",
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.sp
                    )
                }
            } else if (!isSuccess) {
                // Animated Laser Line
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val y = size.height * laserPosition
                    drawLine(
                        color = Color(0xFF38BDF8),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 4f
                    )
                }
            } else {
                // Success Badge overlay inside the viewfinder
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "PAIRED & CONNECTED",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp,
                        letterSpacing = 1.sp
                    )
                    if (detectedPayload.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = detectedPayload.take(45) + (if (detectedPayload.length > 45) "..." else ""),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFF6EE7B7),
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Redirecting to Dashboard...",
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
