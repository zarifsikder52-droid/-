package com.example.ui.screens

import android.Manifest
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.viewmodel.ChatViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlin.math.sin

@OptIn(ExperimentalPermissionsApi::class, ExperimentalLayoutApi::class)
@Composable
fun CallScreen(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val activeChatUser by viewModel.activeChatUser.collectAsState()
    val callDetails by viewModel.callDetails.collectAsState()
    val callType = callDetails?.type ?: "audio" // "audio" or "video"
    val callStatus by viewModel.callStatus.collectAsState() // "ringing", "calling", "connected"
    val callTimerSeconds by viewModel.callTimerSeconds.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isCameraOn by viewModel.isCameraOn.collectAsState()
    val signalingLogs by viewModel.signalingLogs.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Display / Simulation filters for face-to-face video stream
    var activeRemoteFilter by remember { mutableStateOf("clear") } // "clear", "thermal", "neon", "cyber"
    var activeRemoteSource by remember { mutableStateOf("scenic") } // "scenic", "wave", "avatar"
    var showSignalingConsole by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(true) }

    // Camera permission state from Accompanist
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Convert seconds to readable MM:SS
    val durationText = remember(callTimerSeconds) {
        val mins = callTimerSeconds / 60
        val secs = callTimerSeconds % 60
        String.format("%02d:%02d", mins, secs)
    }

    // Color matrix filters for simulating thermal, neon, cyber visions on decoded face-to-face stream
    val colorMatrix = remember(activeRemoteFilter) {
        when (activeRemoteFilter) {
            "thermal" -> ColorMatrix(floatArrayOf(
                0f, 1f, 0f, 0f, 0f, // R
                0f, 0f, 1f, 0f, 0f, // G
                1f, 0f, 0f, 0f, 0f, // B
                0f, 0f, 0f, 1f, 0f  // A
            ))
            "cyber" -> ColorMatrix(floatArrayOf(
                1.5f, 0f, 0f, 0f, -50f,
                0f, 0.5f, 0f, 0f, 0f,
                0f, 0f, 1.5f, 0f, 50f,
                0f, 0f, 0f, 1f, 0f
            ))
            "neon" -> ColorMatrix(floatArrayOf(
                0.2f, 0.8f, 0.2f, 0f, 0f,
                0.8f, 0.2f, 0.2f, 0f, 0f,
                0.2f, 0.2f, 0.8f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            else -> ColorMatrix() // clear/identity
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115)) // Ultra dark cosmic navy background
            .testTag("call_screen_overlay")
    ) {
        // Outer decorative background mesh
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1F2937).copy(alpha = 0.35f), Color.Transparent),
                    radius = size.width
                ),
                center = center
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .statusBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Section: Remote party info and call status
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    text = activeChatUser?.fullname ?: "HostNibo User",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (callStatus == "connected") Color(0xFF00E676)
                                else Color(0xFFFFB300)
                            )
                    )
                    Text(
                        text = when (callStatus) {
                            "ringing" -> "Incoming Call..."
                            "calling" -> "Ringing..."
                            "connected" -> "Connected - $durationText"
                            else -> "Connecting..."
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.LightGray
                    )
                }
            }

            // Central Video Area OR Audio Profile Area
            if (callType == "video" && callStatus == "connected") {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp))
                        .background(Color(0xFF1E222A))
                ) {
                    // REMOTE stream simulation
                    when (activeRemoteSource) {
                        "scenic" -> {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(activeChatUser?.avatarUrl ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Remote Video Feed",
                                contentScale = ContentScale.Crop,
                                colorFilter = ColorFilter.colorMatrix(colorMatrix),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        "wave" -> {
                            // High-fidelity dynamic Canvas wave representing WebRTC visualizer
                            val pulseTime = remember { mutableStateOf(0f) }
                            LaunchedEffect(key1 = true) {
                                while (true) {
                                    pulseTime.value += 0.05f
                                    delay(16)
                                }
                            }
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                clipRect {
                                    val midY = size.height / 2f
                                    val points = 80
                                    val widthStep = size.width / points
                                    for (i in 0 until points) {
                                        val x = i * widthStep
                                        val waveOffset = sin(i * 0.15f + pulseTime.value) * 60f
                                        val noiseOffset = sin(i * 0.4f - pulseTime.value * 2f) * 20f
                                        val y = midY + waveOffset + noiseOffset
                                        drawCircle(
                                            color = Color(0xFF4E3593).copy(alpha = 0.7f),
                                            radius = 4.dp.toPx(),
                                            center = androidx.compose.ui.geometry.Offset(x, y)
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            // Static fallback avatar with animated pulses
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                val pulseRadius = remember { mutableStateOf(100f) }
                                LaunchedEffect(key1 = true) {
                                    while (true) {
                                        pulseRadius.value = (pulseRadius.value + 4f)
                                        if (pulseRadius.value > 300f) pulseRadius.value = 100f
                                        delay(30)
                                    }
                                }
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawCircle(
                                        color = Color(0xFF4E3593).copy(alpha = (1f - (pulseRadius.value - 100f) / 200f).coerceIn(0f, 0.4f)),
                                        radius = pulseRadius.value.dp.toPx(),
                                        center = center
                                    )
                                }
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(activeChatUser?.avatarUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Fallback remote feed",
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(CircleShape)
                                        .background(Color.Gray)
                                )
                            }
                        }
                    }

                    // Bottom pill overlay to switch simulated remote visual filters (Neon, Thermal, scenic webcam)
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterVintage,
                            contentDescription = "Visual Filters",
                            tint = Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                        listOf("clear", "thermal", "neon", "cyber").forEach { filter ->
                            Text(
                                text = filter.replaceFirstChar { it.uppercase() },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeRemoteFilter == filter) Color(0xFF4E3593) else Color.White,
                                modifier = Modifier
                                    .clickable { activeRemoteFilter = filter }
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }

                    // Switch source overlay to toggle wave or scenic webcam preview
                    IconButton(
                        onClick = {
                            activeRemoteSource = when (activeRemoteSource) {
                                "scenic" -> "wave"
                                "wave" -> "avatar"
                                else -> "scenic"
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 12.dp)
                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cached,
                            contentDescription = "Simulated WebRTC Video Stream Source",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // LOCAL camera preview PIP container (floating and togglable)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(110.dp, 160.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                            .background(Color(0xFF232A34))
                    ) {
                        if (isCameraOn) {
                            if (cameraPermissionState.status.isGranted) {
                                // Live process camera preview using CameraX preview
                                val cameraSelector = if (useFrontCamera) {
                                    CameraSelector.DEFAULT_FRONT_CAMERA
                                } else {
                                    CameraSelector.DEFAULT_BACK_CAMERA
                                }
                                AndroidView(
                                    factory = { ctx ->
                                        val previewView = PreviewView(ctx).apply {
                                            scaleType = PreviewView.ScaleType.FILL_CENTER
                                        }
                                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                        cameraProviderFuture.addListener({
                                            val cameraProvider = cameraProviderFuture.get()
                                            val previewUseCase = Preview.Builder().build().apply {
                                                setSurfaceProvider(previewView.surfaceProvider)
                                            }
                                            try {
                                                cameraProvider.unbindAll()
                                                cameraProvider.bindToLifecycle(
                                                    lifecycleOwner,
                                                    cameraSelector,
                                                    previewUseCase
                                                )
                                            } catch (exc: Exception) {
                                                Log.e("CallScreen", "Use case binding failed", exc)
                                            }
                                        }, ContextCompat.getMainExecutor(ctx))
                                        previewView
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    update = { previewView ->
                                        // Update preview selector if it changed
                                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                                        cameraProviderFuture.addListener({
                                            val cameraProvider = cameraProviderFuture.get()
                                            val previewUseCase = Preview.Builder().build().apply {
                                                setSurfaceProvider(previewView.surfaceProvider)
                                            }
                                            try {
                                                cameraProvider.unbindAll()
                                                cameraProvider.bindToLifecycle(
                                                    lifecycleOwner,
                                                    if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                                                    previewUseCase
                                                )
                                            } catch (exc: Exception) {
                                                Log.e("CallScreen", "Live switch failed", exc)
                                            }
                                        }, ContextCompat.getMainExecutor(context))
                                    }
                                )

                                // Direct camera flipping button inside the local PIP window
                                IconButton(
                                    onClick = { useFrontCamera = !useFrontCamera },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(28.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FlipCameraAndroid,
                                        contentDescription = "Flip Local Camera",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else {
                                // Camera permission not granted block with trigger action
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "No Camera Access",
                                        tint = Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Allow Camera",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier
                                            .background(Color(0xFF4E3593), RoundedCornerShape(4.dp))
                                            .clickable { cameraPermissionState.launchPermissionRequest() }
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        } else {
                            // Camera preview disabled status view
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideocamOff,
                                    contentDescription = "Camera Feed Off",
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    // Semi-transparent real-time WebRTC Signaling status Overlay log box (HUD)
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .width(180.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00FFCC))
                            )
                            Text(
                                text = "WebRTC Signal HUD",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00FFCC)
                            )
                        }
                        val displayedLogs = signalingLogs.takeLast(2)
                        if (displayedLogs.isNotEmpty()) {
                            displayedLogs.forEach { logLine ->
                                Text(
                                    text = logLine.substringAfter("] "),
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White.copy(alpha = 0.8f),
                                    maxLines = 1
                                )
                            }
                        } else {
                            Text(
                                text = "Negotiating stream...",
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            } else {
                // Audio call visualizer avatar display (Standard audio or ringing mode)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(240.dp)
                    ) {
                        // Ambient radar pulsator surrounding caller profile
                        val ringPulse = remember { mutableStateOf(160f) }
                        LaunchedEffect(key1 = true) {
                            while (true) {
                                ringPulse.value = (ringPulse.value + 2f)
                                if (ringPulse.value > 240f) ringPulse.value = 160f
                                delay(20)
                            }
                        }
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = Color(0xFF4E3593).copy(alpha = (1f - (ringPulse.value - 160f) / 80f).coerceIn(0f, 0.25f)),
                                radius = ringPulse.value.dp.toPx() / 2f
                            )
                        }

                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(activeChatUser?.avatarUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Caller Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(150.dp)
                                .clip(CircleShape)
                                .border(2.dp, Color(0xFF4E3593), CircleShape)
                                .background(Color.Gray)
                        )
                    }
                }
            }

            // Real-time complete WebRTC signaling diagnostics terminal drawer trigger
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .clickable { showSignalingConsole = !showSignalingConsole }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (showSignalingConsole) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = "Toggle Terminal Console",
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "WebRTC Diagnostics Console",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.LightGray
                    )
                }

                AnimatedVisibility(
                    visible = showSignalingConsole,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0A0B0E))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        val listState = rememberLazyListState()
                        LaunchedEffect(signalingLogs.size) {
                            if (signalingLogs.isNotEmpty()) {
                                listState.animateScrollToItem(signalingLogs.size - 1)
                            }
                        }

                        if (signalingLogs.isEmpty()) {
                            Text(
                                text = "Terminal Idle. Initiating signaling connection...",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(signalingLogs) { logLine ->
                                    val logColor = when {
                                        logLine.contains("Error") -> Color(0xFFFF5252)
                                        logLine.contains("WebRTC") -> Color(0xFF00FFCC)
                                        logLine.contains("Signaling") -> Color(0xFFFFAB40)
                                        else -> Color.White.copy(alpha = 0.9f)
                                    }
                                    Text(
                                        text = logLine,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = logColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom controls button panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (callStatus == "ringing") {
                    // Incoming response panel (Decline or Accept)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Decline action button
                        IconButton(
                            onClick = { viewModel.rejectIncomingCall() },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFFE53935)),
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .testTag("decline_call_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Decline Call",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        // Accept action button
                        IconButton(
                            onClick = { viewModel.acceptIncomingCall() },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF00A884)),
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .testTag("accept_call_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Accept Call",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                } else {
                    // Ongoing or outgoing call controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Local microphone mute button
                        IconButton(
                            onClick = { viewModel.toggleMute() },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (isMuted) Color.White else Color.White.copy(alpha = 0.12f)
                            ),
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mute Microphone",
                                tint = if (isMuted) Color.Black else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Hangup call button
                        IconButton(
                            onClick = { viewModel.endActiveCall() },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFFE53935)),
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .testTag("hangup_call_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "End Active Call",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        // Camera toggle button
                        IconButton(
                            onClick = { viewModel.toggleCamera() },
                            enabled = callType == "video",
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (!isCameraOn) Color.White else Color.White.copy(alpha = 0.12f),
                                disabledContainerColor = Color.White.copy(alpha = 0.04f)
                            ),
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                        ) {
                            Icon(
                                imageVector = if (!isCameraOn) Icons.Default.VideocamOff else Icons.Default.Videocam,
                                contentDescription = "Toggle Camera View",
                                tint = if (!isCameraOn) Color.Black else if (callType == "video") Color.White else Color.DarkGray,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
