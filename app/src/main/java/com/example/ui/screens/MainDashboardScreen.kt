package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.*
import com.example.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    viewModel: ChatViewModel,
    onChatSelected: () -> Unit,
    onLogoutSuccess: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val recentChats by viewModel.recentChats.collectAsState()
    val allMessages by viewModel.allMessages.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()

    var showSearchDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Chats, 1 = Calls
    var chatSearchQuery by remember { mutableStateOf("") }

    val context = LocalContext.current

    Scaffold(
        topBar = {
            val serverHost = remember {
                try {
                    java.net.URI(com.example.data.api.RetrofitClient.BASE_URL).host ?: "HostNibo Chat"
                } catch (e: Exception) {
                    "HostNibo Chat"
                }
            }
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = {
                        Text(
                            text = "RChat",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        currentUser?.let { user ->
                            Box(
                                modifier = Modifier
                                    .padding(start = 12.dp, end = 8.dp)
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .clickable { showProfileDialog = true }
                            ) {
                                val hasRealAvatar = !user.profilePic.isNullOrEmpty() || !user.profile_pic.isNullOrEmpty()
                                if (hasRealAvatar) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(user.avatarUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "My Profile",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    val initials = remember(user.fullname) {
                                        val parts = user.fullname.trim().split("\\s+".toRegex())
                                        if (parts.size >= 2) {
                                            "${parts[0].firstOrNull()?.uppercase() ?: ""}${parts[1].firstOrNull()?.uppercase() ?: ""}"
                                        } else if (parts.isNotEmpty()) {
                                            parts[0].take(2).uppercase()
                                        } else {
                                            "?"
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFFDCE775)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = initials,
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showSearchDialog = true },
                            modifier = Modifier.testTag("search_icon")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search Users", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.testTag("dashboard_appbar")
                )
                if (isOffline) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF3CD))
                            .padding(vertical = 6.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Offline Mode",
                                tint = Color(0xFF856404),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Offline Mode - Showing Cached Chats",
                                color = Color(0xFF856404),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f), thickness = 1.dp)
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showSearchDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("search_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = "New Conversation",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.White)
        ) {
            when (selectedTab) {
                0 -> {
                    // Chats list
                    if (recentChats.isEmpty()) {
                        // Empty state view
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                tint = Color(0xFF1565C0).copy(alpha = 0.4f),
                                modifier = Modifier.size(96.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No Conversations Yet",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.DarkGray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap the search or message button below to start a chat!",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
                        ) {
                            items(recentChats) { chat ->
                                val myUid = currentUser?.uid ?: ""
                                val convId = viewModel.getConversationId(myUid, chat.uid)
                                val lastMsg = allMessages.filter { it.conversationId == convId }.lastOrNull()

                                RecentChatItem(
                                    chat = chat,
                                    lastMsg = lastMsg,
                                    myUid = myUid,
                                    onClick = {
                                        viewModel.selectChat(chat)
                                        onChatSelected()
                                    }
                                )
                                HorizontalDivider(color = Color(0xFFF1F1F1), thickness = 0.8.dp, modifier = Modifier.padding(start = 80.dp, end = 16.dp))
                            }
                        }
                    }
                }
                1 -> {
                    // Calls Tab Screen
                    val callLogs by viewModel.callLogs.collectAsState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Create Call Link Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { /* static */ }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(Color(0xFF1E88E5), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Create call link", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Share a link for your HostNibo call", fontSize = 13.sp, color = Color.Gray)
                            }
                        }

                        // Recent calls header
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF5F5F5))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Recent", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                        }

                        if (callLogs.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = null,
                                    tint = Color(0xFF1E88E5).copy(alpha = 0.3f),
                                    modifier = Modifier.size(80.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No call history yet",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Your real-time call logs will appear here when you make or receive calls inside chats!",
                                    fontSize = 13.sp,
                                    color = Color.LightGray,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        } else {
                            callLogs.forEach { log ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { /* static */ }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .background(Color.LightGray, CircleShape)
                                    ) {
                                        if (log.userPic != null) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(log.userPic)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = log.userName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape)
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(32.dp).align(Alignment.Center)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(log.userName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (log.isIncoming) Icons.Default.CallReceived else Icons.Default.CallMade,
                                                contentDescription = null,
                                                tint = if (log.isIncoming) Color(0xFF1E88E5) else Color.Red,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(log.timeLabel, fontSize = 13.sp, color = Color.Gray)
                                        }
                                    }
                                    IconButton(onClick = { /* start call on contact */ }) {
                                        Icon(
                                            imageVector = if (log.type == "video") Icons.Default.Videocam else Icons.Default.Call,
                                            contentDescription = "Call",
                                            tint = Color(0xFF1565C0)
                                        )
                                    }
                                }
                                HorizontalDivider(color = Color(0xFFF1F1F1), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Custom overlay panels
    if (showSearchDialog) {
        SearchUsersDialog(
            viewModel = viewModel,
            onDismiss = { showSearchDialog = false },
            onUserSelected = { user ->
                showSearchDialog = false
                viewModel.selectChat(
                    RecentChat(
                        uid = user.uid,
                        fullname = user.fullname,
                        username = user.username,
                        profilePic = user.avatarUrl,
                        unread = 0
                    )
                )
                onChatSelected()
            }
        )
    }

    if (showProfileDialog) {
        EditProfileDialog(
            viewModel = viewModel,
            onDismiss = { showProfileDialog = false },
            onLogout = {
                showProfileDialog = false
                viewModel.logout {
                    onLogoutSuccess()
                }
            }
        )
    }
}

@Composable
fun RecentChatItem(
    chat: RecentChat,
    lastMsg: Message?,
    myUid: String?,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("chat_item_${chat.uid}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar: Custom or Initials Fallback
        Box(
            modifier = Modifier.size(56.dp)
        ) {
            val hasRealAvatar = !chat.profilePic.isNullOrEmpty() || !chat.profile_pic.isNullOrEmpty()
            if (hasRealAvatar) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(chat.avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = chat.fullname,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                val initials = remember(chat.fullname) {
                    val parts = chat.fullname.trim().split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        "${parts[0].firstOrNull()?.uppercase() ?: ""}${parts[1].firstOrNull()?.uppercase() ?: ""}"
                    } else if (parts.isNotEmpty()) {
                        parts[0].take(2).uppercase()
                    } else {
                        "?"
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color(0xFFE2F497)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Center Content Column: Name and Username ONLY (same to same as image)
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = chat.fullname,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = "@${chat.username}",
                fontSize = 14.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right side: Green notification unread count badge ONLY (same to same as image)
        if (chat.unread > 0) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF25D366), RoundedCornerShape(100.dp))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = chat.unread.toString(),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchUsersDialog(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    onUserSelected: (SimpleUser) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = query,
                            onValueChange = {
                                query = it
                                viewModel.searchUsers(it)
                            },
                            placeholder = { Text("Search by username/name...", fontSize = 15.sp, color = Color.Gray) },
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                focusedContainerColor = Color(0xFFEDE9F2),
                                unfocusedContainerColor = Color(0xFFEDE9F2),
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("search_query_input")
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("search_back")) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Go Back", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color.White)
            ) {
                if (isSearching) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(36.dp)
                    )
                } else if (searchResults.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (query.isEmpty()) "Find your friends" else "No users found",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(searchResults) { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onUserSelected(user) }
                                    .padding(horizontal = 20.dp, vertical = 14.dp)
                                    .testTag("search_result_user_${user.uid}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(user.avatarUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = user.fullname,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = user.fullname,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "@${user.username}",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            HorizontalDivider(color = Color(0xFFF0F0F0))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileDialog(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    onLogout: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val progressMsg by viewModel.uploadProgressMsg.collectAsState()

    var name by remember { mutableStateOf(currentUser?.fullname ?: "") }
    var username by remember { mutableStateOf(currentUser?.username ?: "") }
    var email by remember { mutableStateOf(currentUser?.email ?: "") }
    var phone by remember { mutableStateOf(currentUser?.phone ?: "") }
    var password by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("chat_prefs", android.content.Context.MODE_PRIVATE) }
    var supabaseEnabled by remember { mutableStateOf(prefs.getBoolean("supabase_enabled", true)) }
    var supabaseUrl by remember { mutableStateOf(prefs.getString("supabase_url", "") ?: "") }
    var supabaseAnonKey by remember { mutableStateOf(prefs.getString("supabase_anon_key", "") ?: "") }

    // Launchers for profile avatar gallery selection
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.updateProfilePic(context, uri)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Edit Profile", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("profile_back")) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Close", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (name.isBlank() || username.isBlank() || email.isBlank() || phone.isBlank()) {
                                    errorMsg = "Please fill in all non-password fields"
                                    return@IconButton
                                }
                                prefs.edit()
                                    .putBoolean("supabase_enabled", supabaseEnabled)
                                    .putString("supabase_url", supabaseUrl.trim())
                                    .putString("supabase_anon_key", supabaseAnonKey.trim())
                                    .apply()
                                isLoading = true
                                errorMsg = null
                                viewModel.updateProfile(
                                    name = name,
                                    username = username,
                                    email = email,
                                    phone = phone,
                                    password = if (password.isNotBlank()) password else null,
                                    onSuccess = {
                                        isLoading = false
                                        onDismiss()
                                    },
                                    onError = { err ->
                                        isLoading = false
                                        errorMsg = err
                                    }
                                )
                            },
                            modifier = Modifier.testTag("profile_save_btn")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            } else {
                                Icon(Icons.Default.Check, contentDescription = "Save Changes", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Avatar Picture Upload Circle
                Box(
                    modifier = Modifier.padding(bottom = 16.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(currentUser?.avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "My Big Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color.LightGray)
                    )

                    IconButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF075E54)),
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Change avatar",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (progressMsg != null) {
                    Text(
                        text = progressMsg!!,
                        color = Color(0xFF075E54),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Fields
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedLabelColor = Color(0xFF1E88E5),
                        unfocusedLabelColor = Color.Gray,
                        focusedBorderColor = Color(0xFF1E88E5),
                        unfocusedBorderColor = Color.LightGray
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input")
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedLabelColor = Color(0xFF1E88E5),
                        unfocusedLabelColor = Color.Gray,
                        focusedBorderColor = Color(0xFF1E88E5),
                        unfocusedBorderColor = Color.LightGray
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_username_input")
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedLabelColor = Color(0xFF1E88E5),
                        unfocusedLabelColor = Color.Gray,
                        focusedBorderColor = Color(0xFF1E88E5),
                        unfocusedBorderColor = Color.LightGray
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_email_input")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedLabelColor = Color(0xFF1E88E5),
                        unfocusedLabelColor = Color.Gray,
                        focusedBorderColor = Color(0xFF1E88E5),
                        unfocusedBorderColor = Color.LightGray
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_phone_input")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("New Password (optional)") },
                    placeholder = { Text("Leave blank to keep current") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedLabelColor = Color(0xFF1E88E5),
                        unfocusedLabelColor = Color.Gray,
                        focusedBorderColor = Color(0xFF1E88E5),
                        unfocusedBorderColor = Color.LightGray
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_password_input")
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "Supabase Backend Configuration",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Supabase", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                        Text("Route all data through your custom Supabase", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = supabaseEnabled,
                        onCheckedChange = { supabaseEnabled = it },
                        modifier = Modifier.testTag("supabase_toggle")
                    )
                }

                OutlinedTextField(
                    value = supabaseUrl,
                    onValueChange = { supabaseUrl = it },
                    label = { Text("Supabase Project URL") },
                    placeholder = { Text("https://your-project.supabase.co") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = Color.Gray,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.LightGray
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("supabase_url_input")
                )

                OutlinedTextField(
                    value = supabaseAnonKey,
                    onValueChange = { supabaseAnonKey = it },
                    label = { Text("Supabase Public Anon Key") },
                    placeholder = { Text("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = Color.Gray,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.LightGray
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("supabase_key_input")
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE53935),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("profile_logout_btn")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Log Out", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
