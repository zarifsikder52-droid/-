package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
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

                            // SMS Simulation Banner
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Sms,
                                        contentDescription = "SMS Simulator",
                                        tint = primaryColor,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "RChat SMS Simulator",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryColor
                                        )
                                        Text(
                                            text = "Your RChat OTP code is: $generatedOtp",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
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
                                    modifier = Modifier.height(50.dp)
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
