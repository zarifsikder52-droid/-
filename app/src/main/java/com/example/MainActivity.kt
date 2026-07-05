package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.CallScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.LoginRegisterScreen
import com.example.ui.screens.MainDashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ChatViewModel

enum class NavigationState {
    CHECKING_AUTH,
    AUTH,
    DASHBOARD,
    CHAT
}

class MainActivity : ComponentActivity() {
    private lateinit var mainViewModel: ChatViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: ChatViewModel = viewModel()
                mainViewModel = viewModel

                var currentNavState by remember { mutableStateOf(NavigationState.CHECKING_AUTH) }

                val isCheckingAuth by viewModel.isCheckingAuth.collectAsState()
                val currentUser by viewModel.currentUser.collectAsState()
                val callStatus by viewModel.callStatus.collectAsState()

                LaunchedEffect(Unit) {
                    handleDeepLink(intent, viewModel)
                }

                // Sync the Auth state to direct the user to the correct screen
                LaunchedEffect(isCheckingAuth, currentUser) {
                    if (!isCheckingAuth) {
                        currentNavState = if (currentUser != null) {
                            NavigationState.DASHBOARD
                        } else {
                            NavigationState.AUTH
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Crossfade(
                            targetState = currentNavState,
                            label = "MainScreenNavigation"
                        ) { state ->
                            when (state) {
                                NavigationState.CHECKING_AUTH -> {
                                    // Beautiful loading spinner splash state
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.White)
                                            .testTag("splash_loading"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Text(
                                                text = "HostNibo Chat",
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFF1E88E5)
                                            )
                                            CircularProgressIndicator(
                                                color = Color(0xFF1E88E5),
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    }
                                }
                                NavigationState.AUTH -> {
                                    LoginRegisterScreen(
                                        viewModel = viewModel,
                                        onLoginSuccess = {
                                            currentNavState = NavigationState.DASHBOARD
                                        }
                                    )
                                }
                                NavigationState.DASHBOARD -> {
                                    MainDashboardScreen(
                                        viewModel = viewModel,
                                        onChatSelected = {
                                            currentNavState = NavigationState.CHAT
                                        },
                                        onLogoutSuccess = {
                                            currentNavState = NavigationState.AUTH
                                        }
                                    )
                                }
                                NavigationState.CHAT -> {
                                    BackHandler {
                                        viewModel.clearActiveChat()
                                        currentNavState = NavigationState.DASHBOARD
                                    }

                                    ChatScreen(
                                        viewModel = viewModel,
                                        onBack = {
                                            viewModel.clearActiveChat()
                                            currentNavState = NavigationState.DASHBOARD
                                        },
                                        onInitiateCall = {
                                            // The CallScreen overlay will handle the Call view
                                        }
                                    )
                                }
                            }
                        }

                        // WebRTC Overlay Call dialog, showing immediately whenever server triggers signaling calls!
                        if (callStatus != "idle") {
                            CallScreen(
                                viewModel = viewModel,
                                onDismiss = {
                                    // Handled internally, but clean up state just in case
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::mainViewModel.isInitialized) {
            handleDeepLink(intent, mainViewModel)
        }
    }

    private fun handleDeepLink(intent: android.content.Intent?, viewModel: ChatViewModel) {
        val uri = intent?.data
        if (uri != null && uri.scheme == "hostnibochat" && uri.host == "login-callback") {
            // Check if fragment is present (usually fragment # contains parameters for OAuth in Supabase)
            val fragment = uri.fragment
            if (!fragment.isNullOrEmpty()) {
                val params = fragment.split("&").associate {
                    val parts = it.split("=")
                    if (parts.size >= 2) parts[0] to parts[1] else parts[0] to ""
                }
                val accessToken = params["access_token"]
                if (!accessToken.isNullOrEmpty()) {
                    val prefs = getSharedPreferences("chat_prefs", MODE_PRIVATE)
                    prefs.edit()
                        .putString("supabase_token", accessToken)
                        .apply()
                    viewModel.checkAuthentication()
                }
            } else {
                // Try query parameters
                val accessToken = uri.getQueryParameter("access_token")
                if (!accessToken.isNullOrEmpty()) {
                    val prefs = getSharedPreferences("chat_prefs", MODE_PRIVATE)
                    prefs.edit()
                        .putString("supabase_token", accessToken)
                        .apply()
                    viewModel.checkAuthentication()
                }
            }
        }
    }
}
