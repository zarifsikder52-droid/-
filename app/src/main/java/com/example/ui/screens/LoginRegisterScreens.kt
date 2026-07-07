package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.viewmodel.ChatViewModel
import kotlin.random.Random
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.widget.Toast
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import com.example.data.api.OtpGatewayManager

enum class AuthStep {
    INPUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginRegisterScreen(
    viewModel: ChatViewModel,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(AuthStep.INPUT) }

    // Inputs
    var phoneCountryCode by remember { mutableStateOf("+880") }
    var phoneNum by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    // Simulation / State helpers
    var generatedOtp by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Real OTP Auth states (Sometimes SMS message, sometimes Voice Call)
    var isCallActive by remember { mutableStateOf(false) }
    var isCallIncoming by remember { mutableStateOf(false) }
    var isCallAnswered by remember { mutableStateOf(false) }
    var showSmsNotification by remember { mutableStateOf(false) }
    var callTimer by remember { mutableStateOf(0) }

    // Text To Speech Initialization for Voice Call Delivery
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Initialized successfully
            }
        }
        tts = instance
        onDispose {
            instance.stop()
            instance.shutdown()
        }
    }

    val speakOtpText = remember(generatedOtp, tts) {
        {
            if (generatedOtp.isNotBlank() && tts != null) {
                val digits = generatedOtp.map { it.toString() }.joinToString(", ")
                val speechText = "Hello! This is R-Chat automated security verification service. Your six digit verification code is $digits. I repeat, your code is: $digits. Thank you."
                tts?.language = Locale.US
                tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "otp_code_tts")
            }
        }
    }

    LaunchedEffect(isCallAnswered) {
        if (isCallAnswered) {
            callTimer = 0
            speakOtpText()
            while (isCallAnswered) {
                delay(1000)
                callTimer++
                if (callTimer % 12 == 0) {
                    speakOtpText()
                }
            }
        }
    }

    LaunchedEffect(showSmsNotification) {
        if (showSmsNotification) {
            delay(8000)
            showSmsNotification = false
        }
    }

    val countries = listOf("+880", "+1", "+44", "+91", "+86", "+81", "+49", "+33", "+971")
    var countryMenuExpanded by remember { mutableStateOf(false) }

    val primaryColor = Color(0xFF128C7E)
    val backgroundColor = Color.White

    val cleanPhone = (phoneCountryCode + phoneNum).trim()

    /* galleryLauncher removed */

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Logo
            Box(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .size(72.dp)
                    .background(primaryColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = "Chat Logo",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Text(
                text = "RChat",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = "Safe, fast, and secure messaging",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Step Container Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    
                    // Display current error if any
                    AnimatedVisibility(visible = errorMsg != null) {
                        errorMsg?.let { msg ->
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }

                    // Main switch logic for steps
                    when (currentStep) {
                        AuthStep.INPUT -> {
                            Text(
                                text = "Welcome to RChat",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black,
                                modifier = Modifier.align(Alignment.Start)
                            )

                            Text(
                                text = "Please sign in to continue.",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            
                            Spacer(modifier = Modifier.height(32.dp))

                            Button(
                                onClick = {
                                    viewModel.loginWithGoogle(context)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("google_login_button")
                            ) {
                                Text(
                                    text = "Sign in with Google",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

