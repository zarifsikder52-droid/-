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

enum class AuthStep {
    PHONE_INPUT,
    OTP_INPUT,
    NAME_INPUT,
    AVATAR_INPUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginRegisterScreen(
    viewModel: ChatViewModel,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(AuthStep.PHONE_INPUT) }

    // Inputs
    var phoneCountryCode by remember { mutableStateOf("+880") }
    var phoneNum by remember { mutableStateOf("") }
    var enteredOtp by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }

    // Simulation / State helpers
    var generatedOtp by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Real OTP Auth states (Sometimes SMS message, sometimes Voice Call)
    var otpMethod by remember { mutableStateOf("sms") } // "sms" or "call"
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

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedAvatarUri = uri
    }

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
                        AuthStep.PHONE_INPUT -> {
                            Text(
                                text = "Enter Your Phone Number",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black,
                                modifier = Modifier.align(Alignment.Start)
                            )

                            Text(
                                text = "Please select your country code and enter your active mobile number to proceed.",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                modifier = Modifier.align(Alignment.Start)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(0.35f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFF5F5F5))
                                        .clickable { countryMenuExpanded = true }
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = phoneCountryCode,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray)
                                    }

                                    DropdownMenu(
                                        expanded = countryMenuExpanded,
                                        onDismissRequest = { countryMenuExpanded = false }
                                    ) {
                                        countries.forEach { code ->
                                            DropdownMenuItem(
                                                text = { Text(code) },
                                                onClick = {
                                                    phoneCountryCode = code
                                                    countryMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = phoneNum,
                                    onValueChange = { input -> 
                                        if (input.all { it.isDigit() }) {
                                            phoneNum = input
                                        }
                                    },
                                    placeholder = { Text("Phone Number") },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Phone,
                                        imeAction = ImeAction.Done
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.Black,
                                        unfocusedTextColor = Color.Black,
                                        focusedBorderColor = primaryColor,
                                        unfocusedBorderColor = Color.LightGray
                                    ),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(0.65f)
                                        .testTag("reg_phone_input")
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (phoneNum.isBlank() || phoneNum.length < 6) {
                                        errorMsg = "Please enter a valid phone number"
                                        return@Button
                                    }
                                    errorMsg = null
                                    
                                    // Generate 6-digit random code
                                    generatedOtp = Random.nextInt(100000, 999999).toString()
                                    enteredOtp = ""
                                    
                                    // Sometimes Call (voice), sometimes Message (SMS)
                                    val isCall = Random.nextBoolean()
                                    otpMethod = if (isCall) "call" else "sms"
                                    
                                    if (otpMethod == "call") {
                                        isCallActive = true
                                        isCallIncoming = true
                                        isCallAnswered = false
                                        showSmsNotification = false
                                    } else {
                                        isCallActive = false
                                        isCallIncoming = false
                                        isCallAnswered = false
                                        showSmsNotification = true
                                    }
                                    
                                    // Transition
                                    currentStep = AuthStep.OTP_INPUT
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("submit_button")
                            ) {
                                Text(
                                    text = "Send Verification Code",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        AuthStep.OTP_INPUT -> {
                            Text(
                                text = "Enter Verification Code",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black,
                                modifier = Modifier.align(Alignment.Start)
                            )

                            Text(
                                text = "We've sent a 6-digit verification code to $cleanPhone.",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                modifier = Modifier.align(Alignment.Start)
                            )

                            // Status and Delivery details banner
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (otpMethod == "call") Color(0xFFEFF6FF) else Color(0xFFF0FDF4)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = if (otpMethod == "call") Icons.Rounded.PhoneInTalk else Icons.Rounded.Sms,
                                        contentDescription = "OTP Status",
                                        tint = if (otpMethod == "call") Color(0xFF2563EB) else primaryColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            text = if (otpMethod == "call") "Voice OTP Delivery" else "SMS Message Delivery",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (otpMethod == "call") Color(0xFF2563EB) else primaryColor
                                        )
                                        Text(
                                            text = if (otpMethod == "call") "Receiving phone call to speak OTP code..." else "SMS message delivered containing verification code.",
                                            fontSize = 12.sp,
                                            color = Color.Black
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = enteredOtp,
                                onValueChange = { input ->
                                    if (input.length <= 6 && input.all { it.isDigit() }) {
                                        enteredOtp = input
                                    }
                                },
                                placeholder = { Text("6-Digit OTP Code") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.Black,
                                    unfocusedTextColor = Color.Black,
                                    focusedBorderColor = primaryColor,
                                    unfocusedBorderColor = Color.LightGray
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("otp_input")
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        currentStep = AuthStep.PHONE_INPUT
                                        enteredOtp = ""
                                        errorMsg = null
                                    }
                                ) {
                                    Text("Change Number", color = primaryColor)
                                }

                                TextButton(
                                    onClick = {
                                        // Request the alternate method
                                        generatedOtp = Random.nextInt(100000, 999999).toString()
                                        enteredOtp = ""
                                        errorMsg = null
                                        if (otpMethod == "sms") {
                                            otpMethod = "call"
                                            isCallActive = true
                                            isCallIncoming = true
                                            isCallAnswered = false
                                            showSmsNotification = false
                                        } else {
                                            otpMethod = "sms"
                                            isCallActive = false
                                            isCallIncoming = false
                                            isCallAnswered = false
                                            showSmsNotification = true
                                        }
                                        Toast.makeText(context, "New code sent via ${otpMethod.uppercase()}!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text(
                                        text = if (otpMethod == "sms") "Get Voice Call" else "Send SMS Code",
                                        color = primaryColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    if (enteredOtp != generatedOtp) {
                                        errorMsg = "Incorrect verification code. Please try again."
                                        return@Button
                                    }
                                    errorMsg = null
                                    isLoading = true

                                    // Try to login internally
                                    viewModel.login(
                                        email = "${cleanPhone}@rchat.com",
                                        pass = "pass_${cleanPhone}",
                                        onSuccess = {
                                            isLoading = false
                                            onLoginSuccess()
                                        },
                                        onError = {
                                            // Failed, which means account doesn't exist. Proceed to Register Screen
                                            isLoading = false
                                            currentStep = AuthStep.NAME_INPUT
                                        }
                                    )
                                },
                                enabled = !isLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("verify_otp_button")
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                } else {
                                    Text(
                                        text = "Verify OTP",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        AuthStep.NAME_INPUT -> {
                            Text(
                                text = "What is Your Name?",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black,
                                modifier = Modifier.align(Alignment.Start)
                            )

                            Text(
                                text = "Please enter your first and last name to personalize your RChat profile.",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                modifier = Modifier.align(Alignment.Start)
                            )

                            OutlinedTextField(
                                value = firstName,
                                onValueChange = { firstName = it },
                                label = { Text("First Name") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.Black,
                                    unfocusedTextColor = Color.Black,
                                    focusedBorderColor = primaryColor,
                                    unfocusedBorderColor = Color.LightGray
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reg_firstname_input")
                            )

                            OutlinedTextField(
                                value = lastName,
                                onValueChange = { lastName = it },
                                label = { Text("Last Name (Optional)") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.Black,
                                    unfocusedTextColor = Color.Black,
                                    focusedBorderColor = primaryColor,
                                    unfocusedBorderColor = Color.LightGray
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reg_lastname_input")
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                FloatingActionButton(
                                    onClick = {
                                        if (firstName.trim().isBlank()) {
                                            errorMsg = "First Name is required"
                                            return@FloatingActionButton
                                        }
                                        errorMsg = null
                                        currentStep = AuthStep.AVATAR_INPUT
                                    },
                                    containerColor = primaryColor,
                                    contentColor = Color.White,
                                    shape = CircleShape,
                                    modifier = Modifier.testTag("next_to_avatar_button")
                                ) {
                                    Icon(Icons.Default.ArrowForward, contentDescription = "Next")
                                }
                            }
                        }

                        AuthStep.AVATAR_INPUT -> {
                            Text(
                                text = "Select Profile Picture",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black,
                                modifier = Modifier.align(Alignment.Start)
                            )

                            Text(
                                text = "Add a profile photo so friends can recognize you instantly.",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                modifier = Modifier.align(Alignment.Start)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Avatar display circle
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEEEEEE))
                                    .clickable { galleryLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedAvatarUri != null) {
                                    AsyncImage(
                                        model = selectedAvatarUri,
                                        contentDescription = "Selected Avatar",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Upload Photo",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }

                            TextButton(
                                onClick = { galleryLauncher.launch("image/*") }
                            ) {
                                Text(
                                    text = if (selectedAvatarUri != null) "Change Photo" else "Choose From Gallery",
                                    color = primaryColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        // Skip Avatar and Register
                                        selectedAvatarUri = null
                                        registerUser(
                                            viewModel = viewModel,
                                            firstName = firstName,
                                            lastName = lastName,
                                            cleanPhone = cleanPhone,
                                            selectedAvatarUri = null,
                                            context = context,
                                            onSuccess = onLoginSuccess,
                                            onError = { errorMsg = it },
                                            setLoading = { isLoading = it }
                                        )
                                    },
                                    enabled = !isLoading
                                ) {
                                    Text(
                                        text = "Skip",
                                        color = Color.Gray,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                FloatingActionButton(
                                    onClick = {
                                        if (!isLoading) {
                                            registerUser(
                                                viewModel = viewModel,
                                                firstName = firstName,
                                                lastName = lastName,
                                                cleanPhone = cleanPhone,
                                                selectedAvatarUri = selectedAvatarUri,
                                                context = context,
                                                onSuccess = onLoginSuccess,
                                                onError = { errorMsg = it },
                                                setLoading = { isLoading = it }
                                            )
                                        }
                                    },
                                    containerColor = if (isLoading) Color.Gray else primaryColor,
                                    contentColor = Color.White,
                                    shape = CircleShape,
                                    modifier = Modifier.testTag("complete_register_button")
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                    } else {
                                        Icon(Icons.Default.Check, contentDescription = "Complete")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sliding SMS Notification Banner
        AnimatedVisibility(
            visible = showSmsNotification,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .statusBarsPadding()
                .zIndex(100f)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // SMS Icon with badge
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFE8F5E9), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sms,
                            contentDescription = "SMS",
                            tint = primaryColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Messages • now",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.Gray,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { showSmsNotification = false }
                            )
                        }
                        Text(
                            text = "RChat Verification Support",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Your login verification code is: $generatedOtp. Please use this code to verify your account in the RChat app. Do not share it.",
                            fontSize = 13.sp,
                            color = Color.DarkGray
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("RChat OTP", generatedOtp)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "OTP Code Copied!", Toast.LENGTH_SHORT).show()
                                    showSmsNotification = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Copy Code", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            TextButton(
                                onClick = {
                                    enteredOtp = generatedOtp
                                    showSmsNotification = false
                                    Toast.makeText(context, "OTP Auto-filled!", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Auto-fill", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                            }
                        }
                    }
                }
            }
        }

        // Verification Call Overlay
        AnimatedVisibility(
            visible = isCallActive,
            enter = fadeIn() + expandIn(),
            exit = fadeOut() + shrinkOut(),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(200f)
        ) {
            val darkBgColor = Color(0xFF0F172A) // Sleek slate-900 background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(darkBgColor)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Call Header
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VerifiedUser,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "RChat Verification Support",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isCallIncoming) "Incoming Verification Call..." else "Voice OTP Verification",
                            fontSize = 15.sp,
                            color = Color.LightGray
                        )
                        
                        if (isCallAnswered) {
                            val minutes = callTimer / 60
                            val seconds = callTimer % 60
                            val timerStr = String.format("%02d:%02d", minutes, seconds)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = timerStr,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }

                    // Call Body: Visualizer or pulsing avatar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCallIncoming) {
                            // Pulsing avatar for incoming call
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val scale by infiniteTransition.animateFloat(
                                initialValue = 0.9f,
                                targetValue = 1.15f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "scale"
                            )
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E293B))
                                    .border(2.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size((100 * scale).dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF334155)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PhoneInTalk,
                                        contentDescription = "Pulsing Call",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        } else {
                            // Answered state: show active sound visualizer and subtitles
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                // Sound Visualizer (bouncing bars)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.height(60.dp)
                                ) {
                                    repeat(5) { index ->
                                        val infiniteTransition = rememberInfiniteTransition(label = "bar_$index")
                                        val heightPercent by infiniteTransition.animateFloat(
                                            initialValue = 0.2f,
                                            targetValue = 1.0f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(
                                                    durationMillis = 300 + (index * 120),
                                                    easing = FastOutSlowInEasing
                                                ),
                                                repeatMode = RepeatMode.Reverse
                                            ),
                                            label = "height"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(8.dp)
                                                .fillMaxHeight(heightPercent)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF4ADE80)) // Active green
                                        )
                                    }
                                }
                                
                                // Live Subtitles Card
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "LIVE SUBTITLES",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8),
                                            letterSpacing = 1.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Speaking Code:  ${generatedOtp.map { it.toString() }.joinToString("   ")}",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "\"Your RChat verification code is ${generatedOtp}. Please do not share this code with anyone.\"",
                                            fontSize = 13.sp,
                                            color = Color.LightGray,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Call Actions / Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isCallIncoming) {
                            // Decline Button (Red)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = {
                                        isCallActive = false
                                        isCallIncoming = false
                                        isCallAnswered = false
                                        tts?.stop()
                                        Toast.makeText(context, "Verification call declined", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444))
                                        .testTag("decline_otp_call")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CallEnd,
                                        contentDescription = "Decline Call",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Decline", color = Color.White, fontSize = 12.sp)
                            }

                            // Answer Button (Green)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = {
                                        isCallIncoming = false
                                        isCallAnswered = true
                                    },
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF22C55E))
                                        .testTag("answer_otp_call")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Call,
                                        contentDescription = "Answer Call",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Answer", color = Color.White, fontSize = 12.sp)
                            }
                        } else {
                            // Active call actions: Replay and Hang Up
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = {
                                        speakOtpText()
                                        Toast.makeText(context, "Replaying verification code...", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF334155))
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Replay Code",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Replay Code", color = Color.White, fontSize = 12.sp)
                            }
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = {
                                        isCallActive = false
                                        isCallAnswered = false
                                        tts?.stop()
                                        Toast.makeText(context, "Call ended", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444))
                                        .testTag("hang_up_otp_call")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CallEnd,
                                        contentDescription = "Hang Up",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Hang Up", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun registerUser(
    viewModel: ChatViewModel,
    firstName: String,
    lastName: String,
    cleanPhone: String,
    selectedAvatarUri: Uri?,
    context: android.content.Context,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    setLoading: (Boolean) -> Unit
) {
    setLoading(true)
    val combinedName = if (lastName.isBlank()) firstName.trim() else "${firstName.trim()} ${lastName.trim()}"
    val randomUsernameSuffix = Random.nextInt(1000, 9999).toString()
    val cleanNameForUser = firstName.lowercase().replace(" ", "")
    val generatedUsername = "${cleanNameForUser}_$randomUsernameSuffix"

    viewModel.register(
        name = combinedName,
        username = generatedUsername,
        email = "${cleanPhone}@rchat.com",
        phone = cleanPhone,
        pass = "pass_${cleanPhone}",
        onSuccess = {
            if (selectedAvatarUri != null) {
                viewModel.updateProfilePic(context, selectedAvatarUri)
            }
            setLoading(false)
            onSuccess()
        },
        onError = { err ->
            setLoading(false)
            onError(err)
        }
    )
}
