package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginRegisterScreen(
    viewModel: ChatViewModel,
    onLoginSuccess: () -> Unit
) {
    var isSignUp by remember { mutableStateOf(false) }

    // Text inputs
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phoneCountryCode by remember { mutableStateOf("+880") }
    var phoneNum by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val countries = listOf("+880", "+1", "+44", "+91", "+86", "+81", "+49", "+33", "+971")
    var countryMenuExpanded by remember { mutableStateOf(false) }

    val backgroundColor = Color.White
    val primaryColor = Color(0xFF4E3593)

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
                    .size(64.dp)
                    .background(primaryColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "R",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "RChat",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = if (isSignUp) "Create your account" else "Log in to your account",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Form container
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isSignUp) {
                        // Full Name
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Full Name") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = primaryColor) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                focusedLabelColor = primaryColor,
                                unfocusedLabelColor = Color.Gray,
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = Color.LightGray
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reg_name_input")
                        )

                        // Username
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = primaryColor) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                focusedLabelColor = primaryColor,
                                unfocusedLabelColor = Color.Gray,
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = Color.LightGray
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reg_username_input")
                        )

                        // Phone Number with Country Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(0.4f)
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
                                        fontSize = 14.sp,
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
                                onValueChange = { phoneNum = it },
                                label = { Text("Phone Number") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.Black,
                                    unfocusedTextColor = Color.Black,
                                    focusedLabelColor = primaryColor,
                                    unfocusedLabelColor = Color.Gray,
                                    focusedBorderColor = primaryColor,
                                    unfocusedBorderColor = Color.LightGray
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(0.6f)
                                    .testTag("reg_phone_input")
                            )
                        }
                    }

                    // Email Address
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = primaryColor) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedLabelColor = primaryColor,
                            unfocusedLabelColor = Color.Gray,
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = Color.LightGray
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_input")
                    )

                    // Password
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = primaryColor) },
                        trailingIcon = {
                            val icon = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(icon, contentDescription = "Toggle password visibility")
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedLabelColor = primaryColor,
                            unfocusedLabelColor = Color.Gray,
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = Color.LightGray
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input")
                    )

                    // Error Message display
                    AnimatedVisibility(visible = errorMsg != null) {
                        errorMsg?.let { msg ->
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Action Button
                    Button(
                        onClick = {
                            if (email.isBlank() || password.isBlank()) {
                                errorMsg = "Please fill in all email and password fields"
                                return@Button
                            }
                            if (isSignUp && (name.isBlank() || username.isBlank() || phoneNum.isBlank())) {
                                errorMsg = "Please fill in all fields"
                                return@Button
                            }

                            errorMsg = null
                            isLoading = true

                            if (isSignUp) {
                                val fullPhone = phoneCountryCode + phoneNum
                                viewModel.register(
                                    name = name,
                                    username = username,
                                    email = email,
                                    phone = fullPhone,
                                    pass = password,
                                    onSuccess = {
                                        isLoading = false
                                        onLoginSuccess()
                                    },
                                    onError = { err ->
                                        isLoading = false
                                        errorMsg = err
                                    }
                                )
                            } else {
                                viewModel.login(
                                    email = email,
                                    pass = password,
                                    onSuccess = {
                                        isLoading = false
                                        onLoginSuccess()
                                    },
                                    onError = { err ->
                                        isLoading = false
                                        errorMsg = err
                                    }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("submit_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                text = if (isSignUp) "Sign Up" else "Log In",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val context = androidx.compose.ui.platform.LocalContext.current
                    val prefs = remember(context) { context.getSharedPreferences("chat_prefs", android.content.Context.MODE_PRIVATE) }
                    val supabaseEnabled = prefs.getBoolean("supabase_enabled", true)
                    val supabaseUrl = prefs.getString("supabase_url", "") ?: ""

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    OutlinedButton(
                        onClick = {
                            if (!supabaseEnabled || supabaseUrl.isBlank()) {
                                android.widget.Toast.makeText(context, "Please configure and enable Supabase in profile settings first", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                val cleanUrl = supabaseUrl.removeSuffix("/")
                                val oauthUrl = "$cleanUrl/auth/v1/authorize?provider=google&redirect_to=hostnibochat://login-callback"
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(oauthUrl))
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("google_login_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Login,
                                contentDescription = "Google Icon",
                                tint = primaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Continue with Google",
                                color = primaryColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Switch Mode option
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isSignUp) "Already have an account?" else "Don't have an account?",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                Text(
                    text = if (isSignUp) " Login" else " Sign Up",
                    color = primaryColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable {
                            isSignUp = !isSignUp
                            errorMsg = null
                        }
                        .testTag("toggle_auth_mode")
                )
            }
        }
    }
}
