package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Message
import com.example.data.model.RecentChat
import com.example.ui.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*

data class ChatThemeOption(
    val id: String,
    val name: String,
    val color: Color,
    val isDark: Boolean
)

val ChatThemeOptions = listOf(
    ChatThemeOption("classic", "Classic Teal", Color(0xFFECE5DD), false),
    ChatThemeOption("slate_dark", "Cosmic Slate", Color(0xFF1E1E24), true),
    ChatThemeOption("lavender", "Dreamy Lavender", Color(0xFFF3EBF6), false),
    ChatThemeOption("mint", "Fresh Mint", Color(0xFFE8F5E9), false),
    ChatThemeOption("peach", "Warm Sunset", Color(0xFFFDF2E9), false),
    ChatThemeOption("midnight", "Midnight Navy", Color(0xFF0B141A), true)
)

fun formatChatDate(timeMs: Long): String {
    val messageCalendar = Calendar.getInstance().apply { timeInMillis = timeMs }
    val nowCalendar = Calendar.getInstance()
    return if (nowCalendar.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
        nowCalendar.get(Calendar.DAY_OF_YEAR) == messageCalendar.get(Calendar.DAY_OF_YEAR)) {
        "Today"
    } else if (nowCalendar.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
        nowCalendar.get(Calendar.DAY_OF_YEAR) - messageCalendar.get(Calendar.DAY_OF_YEAR) == 1) {
        "Yesterday"
    } else {
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(timeMs))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onInitiateCall: () -> Unit
) {
    val activeUser by viewModel.activeChatUser.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val uploadProgressMsg by viewModel.uploadProgressMsg.collectAsState()
    val activeUserTyping by viewModel.activeUserTyping.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()

    val currentTheme by viewModel.chatTheme.collectAsState()
    var showThemeSelector by remember { mutableStateOf(false) }
    var showContactProfile by remember { mutableStateOf(false) }
    val blockedUsers by viewModel.blockedUsers.collectAsState()
    val isBlocked = activeUser?.let { u -> blockedUsers.any { it.uid == u.uid } } ?: false

    val currentThemeColor = remember(currentTheme) {
        ChatThemeOptions.find { it.id == currentTheme }?.color ?: Color(0xFFECE5DD)
    }

    var textInput by remember { mutableStateOf("") }
    val messageReactions by viewModel.messageReactions.collectAsState()
    var isSearchingMessages by remember { mutableStateOf(false) }
    var messageSearchQuery by remember { mutableStateOf("") }

    val filteredMessages = remember(messages, messageSearchQuery) {
        if (messageSearchQuery.isBlank()) {
            messages
        } else {
            messages.filter { msg ->
                msg.text?.contains(messageSearchQuery, ignoreCase = true) == true
            }
        }
    }

    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Voice Recording States
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableStateOf(0) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }

    val hasRecordPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRecordPermission.value = isGranted
        if (isGranted) {
            android.widget.Toast.makeText(context, "Permission granted! Tap the microphone button again to record.", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(context, "Microphone permission is required to record audio.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                recordingSeconds++
            }
        }
    }

    val formattedTime = remember(recordingSeconds) {
        val mins = recordingSeconds / 60
        val secs = recordingSeconds % 60
        String.format("%02d:%02d", mins, secs)
    }

    fun startRecording() {
        try {
            val file = File(context.cacheDir, "voice_record_${System.currentTimeMillis()}.mp4")
            audioFile = file

            val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            isRecording = true
        } catch (e: Exception) {
            Log.e("ChatScreen", "Failed to start recording", e)
            android.widget.Toast.makeText(context, "Error starting voice recorder: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun stopAndSendRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            val fileToSend = audioFile
            if (fileToSend != null && fileToSend.exists() && fileToSend.length() > 0) {
                viewModel.uploadAndSendRecordedFile(fileToSend, "voice", "voice_recording.mp4")
            }
            audioFile = null
        } catch (e: Exception) {
            Log.e("ChatScreen", "Failed to stop recording", e)
            android.widget.Toast.makeText(context, "Error saving recording: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
            isRecording = false
        }
    }

    fun cancelRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // Ignore
        }
        mediaRecorder = null
        isRecording = false
        audioFile?.delete()
        audioFile = null
    }

    // Features States
    val recentChats by viewModel.recentChats.collectAsState()
    var selectedMessages by remember { mutableStateOf(setOf<Message>()) }
    var showForwardDialog by remember { mutableStateOf(false) }
    var messagesToForward by remember { mutableStateOf<List<Message>>(emptyList()) }
    var showImageViewer by remember { mutableStateOf<Message?>(null) }
    var userOnlineStatus by remember { mutableStateOf("Online") }

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(isDownloading) {
        if (isDownloading) {
            downloadProgress = 0f
            while (downloadProgress < 1f) {
                kotlinx.coroutines.delay(80)
                downloadProgress += 0.05f
            }
            isDownloading = false
            android.widget.Toast.makeText(context, "Image downloaded successfully to Gallery!", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(activeUser) {
        if (activeUser == null) return@LaunchedEffect
        val isInitiallyOnline = activeUser!!.fullname.length % 2 == 0
        userOnlineStatus = if (isInitiallyOnline) "Online" else "Offline"
        while (true) {
            kotlinx.coroutines.delay(15000)
            userOnlineStatus = if (userOnlineStatus == "Online") "Offline" else "Online"
        }
    }

    // Auto-scroll to bottom of list when messages, typing status, or keyboard visibility updates
    val isFakeTyping = textInput.isNotBlank()
    val isCurrentlyTyping = activeUserTyping || isFakeTyping
    val density = LocalDensity.current
    val keyboardHeight = WindowInsets.ime.getBottom(density)

    LaunchedEffect(messages.size, isCurrentlyTyping, keyboardHeight) {
        val totalCount = messages.size + (if (isCurrentlyTyping) 1 else 0)
        if (totalCount > 0) {
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    // Launchers for Image and File picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && activeUser != null) {
            val (name, _) = getUriDetails(context, uri, "image.png")
            viewModel.uploadAndSendFile(context, uri, "image", name)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && activeUser != null) {
            val (name, _) = getUriDetails(context, uri, "file.bin")
            viewModel.uploadAndSendFile(context, uri, "file", name)
        }
    }

    Scaffold(
        topBar = {
            activeUser?.let { user ->
                if (selectedMessages.isNotEmpty()) {
                    TopAppBar(
                        title = {
                            Text(
                                text = "${selectedMessages.size} selected",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { selectedMessages = emptySet() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear Selection", tint = Color.White)
                            }
                        },
                        actions = {
                            // Copy button
                            IconButton(onClick = {
                                val textToCopy = selectedMessages.mapNotNull { it.text }.joinToString("\n")
                                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Copied Messages", textToCopy)
                                clipboardManager.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                selectedMessages = emptySet()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Messages", tint = Color.White)
                            }

                            // Forward button
                            IconButton(onClick = {
                                messagesToForward = selectedMessages.toList()
                                showForwardDialog = true
                            }) {
                                Icon(Icons.Default.ArrowForward, contentDescription = "Forward Messages", tint = Color.White)
                            }

                            // Delete button (Dustbin)
                            IconButton(onClick = {
                                val idsToDelete = selectedMessages.map { it.id }
                                viewModel.deleteMessages(idsToDelete)
                                selectedMessages = emptySet()
                                android.widget.Toast.makeText(context, "Messages deleted", android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Messages", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("chat_selection_topbar")
                    )
                } else {
                    TopAppBar(
                        title = {
                            if (isSearchingMessages) {
                                OutlinedTextField(
                                    value = messageSearchQuery,
                                    onValueChange = { messageSearchQuery = it },
                                    placeholder = { Text("Search in chat...", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.8f)) },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                messageSearchQuery = ""
                                                isSearchingMessages = false
                                            }
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Close search", tint = Color.White)
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(24.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedContainerColor = Color.White.copy(alpha = 0.15f),
                                        unfocusedContainerColor = Color.White.copy(alpha = 0.15f),
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        cursorColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("msg_search_input")
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable { showContactProfile = true }
                                        .testTag("chat_contact_header")
                                ) {
                                    UserAvatarWithStatus(
                                        avatarUrl = user.avatarUrl,
                                        fullname = user.fullname,
                                        size = 40.dp,
                                        isOnline = (userOnlineStatus == "Online")
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = user.fullname,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "End-to-End Encrypted",
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (isCurrentlyTyping) "Typing..." else if (userOnlineStatus == "Online") "online" else "offline",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCurrentlyTyping) MaterialTheme.colorScheme.primary else if (userOnlineStatus == "Online") MaterialTheme.colorScheme.primary else Color.Gray
                                        )
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    if (isSearchingMessages) {
                                        messageSearchQuery = ""
                                        isSearchingMessages = false
                                    } else {
                                        onBack()
                                    }
                                },
                                modifier = Modifier.testTag("chat_back")
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        actions = {
                            if (!isSearchingMessages) {
                                IconButton(
                                    onClick = { showThemeSelector = true },
                                    modifier = Modifier.testTag("theme_picker_btn")
                                ) {
                                    Icon(Icons.Default.Palette, contentDescription = "Personalize Background", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(
                                    onClick = { isSearchingMessages = true },
                                    modifier = Modifier.testTag("search_msg_btn")
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Search messages", tint = MaterialTheme.colorScheme.primary)
                                }
                                if (!isBlocked) {
                                    IconButton(
                                        onClick = {
                                            viewModel.initiateCall("audio")
                                            onInitiateCall()
                                        },
                                        modifier = Modifier.testTag("audio_call_btn")
                                    ) {
                                        Icon(Icons.Default.Call, contentDescription = "Audio Call", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.initiateCall("video")
                                            onInitiateCall()
                                        },
                                        modifier = Modifier.testTag("video_call_btn")
                                    ) {
                                        Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            navigationIconContentColor = MaterialTheme.colorScheme.primary,
                            actionIconContentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("chat_topbar")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(currentThemeColor)
        ) {
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
                            text = "Offline Mode - Showing Cached Message History",
                            color = Color(0xFF856404),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (filteredMessages.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Text(
                                        text = if (isSearchingMessages) "No messages match \"$messageSearchQuery\"" else "No messages here yet. Say hello!",
                                        color = Color.DarkGray,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = filteredMessages,
                            key = { _, message -> message.id }
                        ) { index, message ->
                            val showDateHeader = if (index == 0) {
                                true
                            } else {
                                val prevMessage = filteredMessages[index - 1]
                                formatChatDate(message.time) != formatChatDate(prevMessage.time)
                            }

                            if (showDateHeader) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFEBF5)),
                                        shape = RoundedCornerShape(16.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                    ) {
                                        Text(
                                            text = formatChatDate(message.time),
                                            color = Color(0xFF706685),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            val isOutgoing = message.sender == currentUser?.uid
                            val reaction = messageReactions[message.id]
                            val isSelected = selectedMessages.contains(message)

                            MessageBubble(
                                message = message,
                                isOutgoing = isOutgoing,
                                reaction = reaction,
                                isSelected = isSelected,
                                recentChats = recentChats,
                                onReact = { emoji -> viewModel.addReaction(message.id, emoji) },
                                onRemoveReaction = { viewModel.removeReaction(message.id) },
                                onLongClick = {
                                    selectedMessages = if (isSelected) {
                                        selectedMessages - message
                                    } else {
                                        selectedMessages + message
                                    }
                                },
                                onClick = {
                                    if (selectedMessages.isNotEmpty()) {
                                        selectedMessages = if (isSelected) {
                                            selectedMessages - message
                                        } else {
                                            selectedMessages + message
                                        }
                                    }
                                },
                                onImageClick = {
                                    showImageViewer = message
                                },
                                onDelete = {
                                    viewModel.deleteMessage(message.id)
                                    android.widget.Toast.makeText(context, "Message deleted", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onForward = { chat ->
                                    viewModel.forwardMessage(message, chat.uid)
                                    android.widget.Toast.makeText(context, "Message forwarded to ${chat.fullname}", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onCopy = {
                                    val textToCopy = message.text ?: message.fileUrl ?: ""
                                    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Copied Message", textToCopy)
                                    clipboardManager.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                    if (isCurrentlyTyping) {
                        item {
                            TypingBubble()
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = uploadProgressMsg != null,
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    uploadProgressMsg?.let { msg ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(text = msg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F2F5)),
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isBlocked) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "You have blocked this contact. Unblock to send messages.",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    if (isRecording) {
                        // High-fidelity active Recording HUD
                        val infiniteTransition = rememberInfiniteTransition(label = "pulsing")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "alpha"
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Pulsing red dot
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color.Red.copy(alpha = alpha))
                            )

                            Text(
                                text = "Recording: $formattedTime",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Red,
                                modifier = Modifier.weight(1f)
                            )

                            // Cancel Button (Red trash icon)
                            IconButton(
                                onClick = { cancelRecording() },
                                modifier = Modifier.testTag("cancel_record_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Cancel Recording",
                                    tint = Color.Red,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Send Button (Check icon)
                            IconButton(
                                onClick = { stopAndSendRecording() },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF25D366)),
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .testTag("send_record_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Stop and Send",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Suggested/Smart Replies Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("Sounds good!", "On it.", "Talk later?").forEach { phrase ->
                                    Box(
                                        modifier = Modifier
                                            .border(
                                                width = 1.dp,
                                                color = Color(0xFFE5DEF5),
                                                shape = RoundedCornerShape(20.dp)
                                            )
                                            .background(Color.White, RoundedCornerShape(20.dp))
                                            .clickable {
                                                viewModel.sendMessage(phrase)
                                            }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = phrase,
                                            color = Color(0xFF1C123F),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // Standard Input Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                var showAttachmentMenu by remember { mutableStateOf(false) }
                                Box {
                                    // Custom outlined circular plus button matching the design exactly
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .border(1.5.dp, Color.Gray, CircleShape)
                                            .clip(CircleShape)
                                            .clickable { showAttachmentMenu = true }
                                            .testTag("attach_btn"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add Attachment",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showAttachmentMenu,
                                        onDismissRequest = { showAttachmentMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Send Image") },
                                            leadingIcon = { Icon(Icons.Default.Photo, contentDescription = null, tint = Color(0xFF1E88E5)) },
                                            onClick = {
                                                showAttachmentMenu = false
                                                imagePickerLauncher.launch("image/*")
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Send Generic File") },
                                            leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null, tint = Color(0xFF25D366)) },
                                            onClick = {
                                                showAttachmentMenu = false
                                                filePickerLauncher.launch("*/*")
                                            }
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = textInput,
                                    onValueChange = {
                                        textInput = it
                                        viewModel.onUserTyping(activeUser?.uid)
                                    },
                                    placeholder = { Text("Type a message...", fontSize = 15.sp) },
                                    singleLine = false,
                                    maxLines = 4,
                                    shape = RoundedCornerShape(24.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.Black,
                                        unfocusedTextColor = Color.Black,
                                        focusedContainerColor = Color(0xFFEDE9F5),
                                        unfocusedContainerColor = Color(0xFFEDE9F5),
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent
                                    ),
                                    trailingIcon = {
                                        IconButton(onClick = { }) {
                                            Icon(
                                                imageVector = Icons.Default.SentimentSatisfiedAlt,
                                                contentDescription = "Emojis",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 40.dp, max = 120.dp)
                                        .testTag("chat_text_input")
                                )

                                if (textInput.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            if (textInput.isNotBlank()) {
                                                viewModel.sendMessage(textInput)
                                                textInput = ""
                                            }
                                        },
                                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .testTag("send_btn")
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            if (hasRecordPermission.value) {
                                                startRecording()
                                            } else {
                                                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        },
                                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .testTag("mic_btn")
                                    ) {
                                        Icon(Icons.Default.Mic, contentDescription = "Record Voice Note", tint = Color.White, modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showForwardDialog) {
        AlertDialog(
            onDismissRequest = { showForwardDialog = false },
            title = { Text("Forward Message to...", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(recentChats) { chat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    messagesToForward.forEach { msg ->
                                        viewModel.forwardMessage(msg, chat.uid)
                                    }
                                    showForwardDialog = false
                                    selectedMessages = emptySet()
                                    android.widget.Toast.makeText(context, "Messages forwarded", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = chat.avatarUrl,
                                contentDescription = chat.fullname,
                                modifier = Modifier.size(36.dp).clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(chat.fullname, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showForwardDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // High fidelity immersive Image Viewer Dialog with View, Download, Forward, Share
    if (showImageViewer != null) {
        val imageMessage = showImageViewer!!
        val fullUrl = if (imageMessage.fileUrl?.startsWith("http") == true) imageMessage.fileUrl else "https://chat.hostnibo.shop/${imageMessage.fileUrl}"
        var showForwardPickerInViewer by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showImageViewer = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            containerColor = Color.Black,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Image Viewer", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { showImageViewer = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(fullUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Fullscreen sent image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.DarkGray)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Download button
                        IconButton(
                            onClick = { isDownloading = true },
                            modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Download Image", tint = Color.White)
                        }

                        // Forward button
                        IconButton(
                            onClick = { showForwardPickerInViewer = true },
                            modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Forward Image", tint = Color.White)
                        }

                        // Share button
                        IconButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Shared image from HostNibo Chat: $fullUrl")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
                            },
                            modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share Image", tint = Color.White)
                        }
                    }
                }
            },
            confirmButton = {}
        )

        if (showForwardPickerInViewer) {
            AlertDialog(
                onDismissRequest = { showForwardPickerInViewer = false },
                title = { Text("Forward Image to...", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                        items(recentChats) { chat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.forwardMessage(imageMessage, chat.uid)
                                        showForwardPickerInViewer = false
                                        showImageViewer = null
                                        android.widget.Toast.makeText(context, "Image forwarded to ${chat.fullname}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = chat.avatarUrl,
                                    contentDescription = chat.fullname,
                                    modifier = Modifier.size(32.dp).clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(chat.fullname, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showForwardPickerInViewer = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    if (isDownloading) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Downloading Image", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LinearProgressIndicator(
                        progress = downloadProgress,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.LightGray,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("${(downloadProgress * 100).toInt()}% completed", fontSize = 14.sp)
                }
            },
            confirmButton = {}
        )
    }

    if (showThemeSelector) {
        AlertDialog(
            onDismissRequest = { showThemeSelector = false },
            title = {
                Text(
                    text = "Customize Chat Wallpaper",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.Black
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "Select a custom background color or theme for your chat conversation:",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ChatThemeOptions.chunked(3).forEach { rowOptions ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowOptions.forEach { option ->
                                    Card(
                                        onClick = {
                                            viewModel.setChatTheme(option.id)
                                        },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (currentTheme == option.id) Color(0xFFF0F2F5) else Color.White
                                        ),
                                        border = if (currentTheme == option.id) {
                                            androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF00A884))
                                        } else {
                                            androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(80.dp)
                                            .testTag("theme_card_${option.id}")
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(option.color)
                                                    .align(Alignment.CenterHorizontally)
                                            ) {
                                                if (currentTheme == option.id) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = if (option.isDark) Color.White else Color.Black,
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .align(Alignment.Center)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = option.name,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.Black,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                                if (rowOptions.size < 3) {
                                    repeat(3 - rowOptions.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showThemeSelector = false },
                    modifier = Modifier.testTag("theme_picker_done")
                ) {
                    Text("Done", color = Color(0xFF00A884), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showContactProfile && activeUser != null) {
        val user = activeUser!!
        AlertDialog(
            onDismissRequest = { showContactProfile = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .testTag("contact_profile_dialog"),
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White,
            confirmButton = {
                TextButton(
                    onClick = { showContactProfile = false },
                    modifier = Modifier.testTag("contact_profile_close")
                ) {
                    Text("Close", color = Color(0xFF075E54), fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Contact Profile",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.Black
                    )
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(user.avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = user.fullname,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color.LightGray)
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = user.fullname,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            text = "@${user.username}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (isBlocked) {
                                viewModel.unblockUser(user.uid)
                            } else {
                                viewModel.blockUser(
                                    uid = user.uid,
                                    fullname = user.fullname,
                                    username = user.username,
                                    profilePic = user.profilePic
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBlocked) Color(0xFF4CAF50) else Color(0xFFE53935),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("block_unblock_btn")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (isBlocked) Icons.Default.CheckCircle else Icons.Default.Block,
                                contentDescription = if (isBlocked) "Unblock" else "Block",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBlocked) "Unblock User" else "Block User",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isOutgoing: Boolean,
    reaction: String?,
    isSelected: Boolean,
    recentChats: List<RecentChat>,
    onReact: (String) -> Unit,
    onRemoveReaction: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onImageClick: () -> Unit,
    onDelete: () -> Unit,
    onForward: (RecentChat) -> Unit,
    onCopy: () -> Unit
) {
    val bubbleColor = if (isOutgoing) Color(0xFF4E3593) else Color(0xFFEFE9FC)
    val alignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleShape = RoundedCornerShape(18.dp)

    var showDropdownMenu by remember { mutableStateOf(false) }
    var showForwardPickerInMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) Color(0xFF2196F3).copy(alpha = 0.15f) else Color.Transparent)
            .padding(vertical = 4.dp, horizontal = 12.dp)
            .testTag("msg_bubble_${message.id}"),
        contentAlignment = alignment
    ) {
        Box(
            contentAlignment = if (isOutgoing) Alignment.BottomEnd else Alignment.BottomStart
        ) {
            if (!isOutgoing) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.widthIn(max = 310.dp)
                ) {
                    val senderChat = recentChats.find { it.uid == message.sender }
                    val avatarUrl = senderChat?.avatarUrl
                    val displayName = senderChat?.fullname ?: "User"
                    if (!avatarUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(avatarUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = displayName,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEFE9FC))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE1D9F1)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayName.take(1).uppercase(),
                                color = Color(0xFF4E3593),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = bubbleColor),
                        shape = bubbleShape,
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(bottom = if (reaction != null) 10.dp else 0.dp)
                            .combinedClickable(
                                onLongClick = { onLongClick() },
                                onClick = {
                                    if (isSelected) {
                                        onClick()
                                    } else {
                                        showDropdownMenu = true
                                    }
                                }
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            when (message.type) {
                                "text" -> {
                                    Text(
                                        text = message.text ?: "",
                                        fontSize = 15.sp,
                                        color = Color(0xFF1C123F)
                                    )
                                }
                                "image" -> {
                                    val fullUrl = if (message.fileUrl?.startsWith("http") == true) message.fileUrl else "https://chat.hostnibo.shop/${message.fileUrl}"
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(fullUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Sent Image",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(160.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.LightGray)
                                            .clickable { onImageClick() }
                                    )
                                }
                                "voice" -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFE5DDF5), RoundedCornerShape(12.dp))
                                            .padding(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = "Voice Note",
                                            tint = Color(0xFF4E3593),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Voice Note",
                                            fontSize = 13.sp,
                                            color = Color(0xFF1C123F),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                else -> {
                                    val filename = message.fileName ?: "Attachment File"
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFE5DDF5), RoundedCornerShape(12.dp))
                                            .padding(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (message.type == "video") Icons.Default.Videocam else Icons.Default.Description,
                                            contentDescription = null,
                                            tint = Color(0xFF4E3593),
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = filename,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1C123F),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.align(Alignment.End),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                                val timeStr = sdf.format(Date(message.time))
                                Text(
                                    text = timeStr,
                                    fontSize = 10.sp,
                                    color = Color(0xFF756E8A)
                                )
                            }
                        }
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = bubbleColor),
                    shape = bubbleShape,
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .widthIn(max = 290.dp)
                        .padding(bottom = if (reaction != null) 10.dp else 0.dp)
                        .combinedClickable(
                            onLongClick = { onLongClick() },
                            onClick = {
                                if (isSelected) {
                                    onClick()
                                } else {
                                    showDropdownMenu = true
                                }
                            }
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        when (message.type) {
                            "text" -> {
                                Text(
                                    text = message.text ?: "",
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                            }
                            "image" -> {
                                val fullUrl = if (message.fileUrl?.startsWith("http") == true) message.fileUrl else "https://chat.hostnibo.shop/${message.fileUrl}"
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(fullUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Sent Image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF3B2773))
                                        .clickable { onImageClick() }
                                )
                            }
                            "voice" -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF3B2773), RoundedCornerShape(12.dp))
                                        .padding(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Voice Note",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Voice Note",
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            else -> {
                                val filename = message.fileName ?: "Attachment File"
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF3B2773), RoundedCornerShape(12.dp))
                                        .padding(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (message.type == "video") Icons.Default.Videocam else Icons.Default.Description,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = filename,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                            val timeStr = sdf.format(Date(message.time))
                            Text(
                                text = timeStr,
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )

                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (message.seen) Icons.Default.DoneAll else Icons.Default.Done,
                                contentDescription = null,
                                tint = if (message.seen) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            if (reaction != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .offset(x = if (isOutgoing) (-12).dp else 12.dp, y = 8.dp)
                        .clickable { onRemoveReaction() }
                ) {
                    Text(
                        text = reaction,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showDropdownMenu,
            onDismissRequest = { showDropdownMenu = false },
            modifier = Modifier
                .background(Color.White)
                .widthIn(min = 200.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val emojis = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")
                emojis.forEach { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 22.sp,
                        modifier = Modifier
                            .clickable {
                                onReact(emoji)
                                showDropdownMenu = false
                            }
                            .padding(4.dp)
                    )
                }
            }

            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))

            DropdownMenuItem(
                text = { Text("Copy") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    showDropdownMenu = false
                    onCopy()
                }
            )

            DropdownMenuItem(
                text = { Text("Forward") },
                leadingIcon = { Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    showForwardPickerInMenu = true
                }
            )

            DropdownMenuItem(
                text = { Text("Delete", color = Color.Red) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp)) },
                onClick = {
                    showDropdownMenu = false
                    onDelete()
                }
            )
        }

        if (showForwardPickerInMenu) {
            AlertDialog(
                onDismissRequest = {
                    showForwardPickerInMenu = false
                    showDropdownMenu = false
                },
                title = { Text("Forward to...", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                        items(recentChats) { chat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onForward(chat)
                                        showForwardPickerInMenu = false
                                        showDropdownMenu = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = chat.avatarUrl,
                                    contentDescription = chat.fullname,
                                    modifier = Modifier.size(32.dp).clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(chat.fullname, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showForwardPickerInMenu = false
                        showDropdownMenu = false
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

fun getUriDetails(context: Context, uri: Uri, fallbackName: String): Pair<String, String?> {
    var name = fallbackName
    val mimeType = context.contentResolver.getType(uri)
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
         val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
         if (nameIndex != -1 && cursor.moveToFirst()) {
             name = cursor.getString(nameIndex)
         }
    }
    return Pair(name, mimeType)
}

@Composable
fun TypingBubble() {
    val bubbleColor = Color.White
    val bubbleShape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 12.dp)

    val infiniteTransition = rememberInfiniteTransition(label = "typing_dots")

    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0.2f at 0 with LinearEasing
                1.0f at 200 with LinearEasing
                0.2f at 400 with LinearEasing
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot1"
    )

    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0.2f at 100 with LinearEasing
                1.0f at 300 with LinearEasing
                0.2f at 500 with LinearEasing
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot2"
    )

    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0.2f at 200 with LinearEasing
                1.0f at 400 with LinearEasing
                0.2f at 600 with LinearEasing
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot3"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 12.dp)
            .testTag("typing_bubble"),
        contentAlignment = Alignment.CenterStart
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            shape = bubbleShape,
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.widthIn(max = 120.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Gray.copy(alpha = dot1Scale)))
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Gray.copy(alpha = dot2Scale)))
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Gray.copy(alpha = dot3Scale)))
            }
        }
    }
}

@Composable
fun UserAvatarWithStatus(
    avatarUrl: String?,
    fullname: String,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    isOnline: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(modifier = modifier.size(size)) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(avatarUrl)
                .crossfade(true)
                .build(),
            contentDescription = fullname,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        )
        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(size * 0.28f)
                    .align(Alignment.BottomEnd)
                    .background(Color.White, CircleShape)
                    .padding((size * 0.04f).coerceAtLeast(1.dp))
                    .background(Color(0xFF25D366), CircleShape)
            )
        }
    }
}
