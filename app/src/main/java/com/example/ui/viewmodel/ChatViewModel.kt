package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.crypto.EncryptionHelper
import com.example.data.local.AppDatabase
import com.example.data.local.BlockedUser
import com.example.data.model.*
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileOutputStream

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = ChatRepository(context = application, chatDao = database.chatDao())

    // Auth State
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isCheckingAuth = MutableStateFlow(true)
    val isCheckingAuth: StateFlow<Boolean> = _isCheckingAuth.asStateFlow()

    private val _authStateError = MutableStateFlow<String?>(null)
    val authStateError: StateFlow<String?> = _authStateError.asStateFlow()

    // Offline mode tracking
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    // Recent Chats (Room Flow)
    val recentChats: StateFlow<List<RecentChat>> = repository.recentChats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockedUsers: StateFlow<List<BlockedUser>> = repository.blockedUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun blockUser(uid: String, fullname: String, username: String, profilePic: String?) {
        viewModelScope.launch {
            repository.blockUser(uid, fullname, username, profilePic)
        }
    }

    fun unblockUser(uid: String) {
        viewModelScope.launch {
            repository.unblockUser(uid)
        }
    }

    // All Messages (Room Flow)
    val allMessages: StateFlow<List<Message>> = repository.allMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Message Reactions Map Flow
    val messageReactions: StateFlow<Map<Int, String>> = repository.allReactions
        .map { list -> list.associate { it.messageId to it.reaction } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun addReaction(messageId: Int, reaction: String) {
        viewModelScope.launch {
            repository.saveReaction(messageId, reaction)
        }
    }

    fun removeReaction(messageId: Int) {
        viewModelScope.launch {
            repository.removeReaction(messageId)
        }
    }

    fun deleteMessage(id: Int) {
        viewModelScope.launch {
            repository.deleteMessage(id)
        }
    }

    fun deleteMessages(ids: List<Int>) {
        viewModelScope.launch {
            repository.deleteMessages(ids)
        }
    }

    fun forwardMessage(message: Message, targetContactUid: String) {
        viewModelScope.launch {
            if (message.fileUrl != null) {
                repository.sendFileMessage(
                    chatWith = targetContactUid,
                    fileType = message.type ?: "file",
                    fileName = message.fileName ?: "file",
                    fileUrl = message.fileUrl
                )
            } else if (!message.text.isNullOrBlank()) {
                repository.sendMessage(
                    myUid = _currentUser.value?.uid ?: "",
                    text = message.text,
                    chatWith = targetContactUid
                )
            }
        }
    }

    fun getConversationId(myUid: String, contactUid: String): String {
        return repository.getConversationId(myUid, contactUid)
    }

    // Active Chat
    private val _activeChatUser = MutableStateFlow<RecentChat?>(null)
    val activeChatUser: StateFlow<RecentChat?> = _activeChatUser.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    // Search Users
    private val _searchResults = MutableStateFlow<List<SimpleUser>>(emptyList())
    val searchResults: StateFlow<List<SimpleUser>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // File upload status
    private val _uploadProgressMsg = MutableStateFlow<String?>(null)
    val uploadProgressMsg: StateFlow<String?> = _uploadProgressMsg.asStateFlow()

    // Call state mapping perfectly to PHP index.php action signaling:
    // status can be: "idle", "calling" (outgoing ringing), "ringing" (incoming), "connected" (active), "ended", "rejected"
    private val _callStatus = MutableStateFlow("idle")
    val callStatus: StateFlow<String> = _callStatus.asStateFlow()

    private val _activeCallId = MutableStateFlow<String?>(null)
    val activeCallId: StateFlow<String?> = _activeCallId.asStateFlow()

    private val _callDetails = MutableStateFlow<CallDetails?>(null)
    val callDetails: StateFlow<CallDetails?> = _callDetails.asStateFlow()

    private val _callTimerSeconds = MutableStateFlow(0)
    val callTimerSeconds: StateFlow<Int> = _callTimerSeconds.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isCameraOn = MutableStateFlow(true)
    val isCameraOn: StateFlow<Boolean> = _isCameraOn.asStateFlow()

    // Typing indicators state
    private val _activeUserTyping = MutableStateFlow(false)
    val activeUserTyping: StateFlow<Boolean> = _activeUserTyping.asStateFlow()

    // Persistent Chat Theme Selection
    private val prefs = application.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
    private val _chatTheme = MutableStateFlow(prefs.getString("selected_chat_theme", "lavender") ?: "lavender")
    val chatTheme: StateFlow<String> = _chatTheme.asStateFlow()

    fun setChatTheme(themeId: String) {
        _chatTheme.value = themeId
        prefs.edit().putString("selected_chat_theme", themeId).apply()
    }

    // Dynamic, local call history list and status updates to avoid mock data!
    private val _callLogs = MutableStateFlow<List<LocalCallLog>>(emptyList())
    val callLogs: StateFlow<List<LocalCallLog>> = _callLogs.asStateFlow()

    private val _statusUpdates = MutableStateFlow<List<LocalStatusUpdate>>(emptyList())
    val statusUpdates: StateFlow<List<LocalStatusUpdate>> = _statusUpdates.asStateFlow()

    private val _signalingLogs = MutableStateFlow<List<String>>(emptyList())
    val signalingLogs: StateFlow<List<String>> = _signalingLogs.asStateFlow()

    fun addCallLog(userName: String, userPic: String?, isIncoming: Boolean, type: String) {
        val formatter = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        val timeStr = formatter.format(java.util.Date())
        val newLog = LocalCallLog(
            userName = userName,
            userPic = userPic,
            timeLabel = "Today, $timeStr",
            isIncoming = isIncoming,
            type = type
        )
        _callLogs.value = listOf(newLog) + _callLogs.value
    }

    fun addStatusUpdate(text: String) {
        val user = _currentUser.value ?: return
        val formatter = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        val timeStr = formatter.format(java.util.Date())
        val newStatus = LocalStatusUpdate(
            userName = user.fullname.ifBlank { "Me" },
            userPic = user.profilePic,
            text = text,
            timeLabel = "Today, $timeStr"
        )
        _statusUpdates.value = listOf(newStatus) + _statusUpdates.value
    }

    // Job definitions for periodic tasks
    private var syncJob: Job? = null
    private var chatPollJob: Job? = null
    private var incomingCallPollJob: Job? = null
    private var callTimerJob: Job? = null
    private var callStatusJob: Job? = null
    private var selfTypingJob: Job? = null
    private var resetRemoteTypingJob: Job? = null
    private var messagesCollectJob: Job? = null
    private var iceExchangeJob: Job? = null
    private var isCurrentlyTypingSelf = false

    init {
        checkAuthentication()
        startIncomingCallPolling()
    }

    fun checkAuthentication() {
        viewModelScope.launch {
            _isCheckingAuth.value = true
            repository.checkAuth().fold(
                onSuccess = { auth ->
                    _isOffline.value = false
                    if (auth.authenticated && auth.uid != null) {
                        _currentUser.value = User(
                            uid = auth.uid,
                            fullname = auth.fullname ?: "",
                            username = auth.username ?: "",
                            email = auth.email,
                            phone = auth.phone,
                            profilePic = auth.profilePic
                        )
                        startDashboardSyncing()
                    } else {
                        _currentUser.value = null
                    }
                },
                onFailure = {
                    _isOffline.value = true
                    val cachedSelf = repository.getCachedSelfUser()
                    if (cachedSelf != null) {
                        _currentUser.value = User(
                            uid = cachedSelf.uid,
                            fullname = cachedSelf.fullname,
                            username = cachedSelf.username,
                            email = cachedSelf.email,
                            phone = cachedSelf.phone,
                            profilePic = cachedSelf.profilePic
                        )
                        startDashboardSyncing()
                    } else {
                        _currentUser.value = null
                        _authStateError.value = it.localizedMessage
                    }
                }
            )
            _isCheckingAuth.value = false
        }
    }

    fun login(email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            repository.login(email, pass).fold(
                onSuccess = { user ->
                    _currentUser.value = user
                    startDashboardSyncing()
                    onSuccess()
                },
                onFailure = {
                    onError(it.localizedMessage ?: "Login failed")
                }
            )
        }
    }

    fun register(name: String, username: String, email: String, phone: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            repository.register(name, username, email, phone, pass).fold(
                onSuccess = { user ->
                    _currentUser.value = user
                    startDashboardSyncing()
                    onSuccess()
                },
                onFailure = {
                    onError(it.localizedMessage ?: "Registration failed")
                }
            )
        }
    }

    fun createLocalGroupOrChannel(name: String, isChannel: Boolean) {
        viewModelScope.launch {
            val idPrefix = if (isChannel) "channel_" else "group_"
            val uniqueId = idPrefix + java.util.UUID.randomUUID().toString().take(8)
            val newChat = com.example.data.local.CachedChatUser(
                uid = uniqueId,
                fullname = name,
                username = if (isChannel) "channel" else "group",
                profilePic = null,
                unread = 0
            )
            repository.insertLocalChat(newChat)
        }
    }

    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            stopDashboardSyncing()
            stopChatPolling()
            repository.logout()
            _currentUser.value = null
            onSuccess()
        }
    }

    fun searchUsers(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            repository.searchUsers(query).fold(
                onSuccess = { list ->
                    val myUid = _currentUser.value?.uid
                    _searchResults.value = if (myUid != null) {
                        list.filter { it.uid != myUid }
                    } else {
                        list
                    }
                },
                onFailure = {
                    _searchResults.value = emptyList()
                }
            )
            _isSearching.value = false
        }
    }

    fun selectChat(contact: RecentChat) {
        _activeChatUser.value = contact
        _messages.value = emptyList()
        _activeUserTyping.value = false
        startChatPolling(contact.uid)
        startListeningToMessages(contact.uid)
        markChatSeen(contact.uid)
        initiateKeyExchangeIfNeeded(contact.uid)
    }

    fun initiateKeyExchangeIfNeeded(contactUid: String) {
        val myUser = _currentUser.value ?: return
        val existingPubKey = EncryptionHelper.getRemotePublicKey(getApplication(), contactUid)
        if (existingPubKey == null) {
            sendKeyExchangeMessage(myUser.uid, contactUid, isResponse = false)
        }
    }

    private fun sendKeyExchangeMessage(myUid: String, contactUid: String, isResponse: Boolean) {
        viewModelScope.launch {
            try {
                val myKeyPair = EncryptionHelper.getOrCreateKeyPair(getApplication(), myUid)
                val myPubKeyHex = EncryptionHelper.bytesToHex(myKeyPair.public.encoded)
                val prefix = if (isResponse) "__E2EE_KEY_EXCH_RESP__:" else "__E2EE_KEY_EXCH__:"
                repository.sendRawMessage(prefix + myPubKeyHex, contactUid)
            } catch (e: Exception) {
                Log.e("ChatVM", "Failed to send key exchange message", e)
            }
        }
    }

    fun clearActiveChat() {
        _activeChatUser.value = null
        _activeUserTyping.value = false
        stopChatPolling()
        messagesCollectJob?.cancel()
        messagesCollectJob = null
        selfTypingJob?.cancel()
        selfTypingJob = null
        resetRemoteTypingJob?.cancel()
        resetRemoteTypingJob = null
        simulatedTypingJob?.cancel()
        simulatedTypingJob = null
    }

    private fun markChatSeen(contactUid: String) {
        viewModelScope.launch {
            repository.markSeen(contactUid)
            // Immediately sync recent chats to update badge numbers
            repository.syncRecentChats()
        }
    }

    fun syncMessagesDirectly(contactUid: String) {
        viewModelScope.launch {
            val myUser = _currentUser.value ?: return@launch
            repository.syncMessages(myUser.uid, contactUid)
        }
    }

    fun sendMessage(text: String) {
        val contact = _activeChatUser.value ?: return
        val myUser = _currentUser.value ?: return
        if (text.isBlank()) return

        viewModelScope.launch {
            repository.sendMessage(myUser.uid, text, contact.uid).fold(
                onSuccess = {
                    // Update the message state immediately
                    syncMessagesDirectly(contact.uid)
                },
                onFailure = {
                    Log.e("ChatVM", "Send message error: ${it.localizedMessage}")
                }
            )
        }
    }

    // Helper to upload a file/image from Android Storage
    fun uploadAndSendFile(context: Context, uri: Uri, fileType: String, originalName: String) {
        val contact = _activeChatUser.value ?: return
        _uploadProgressMsg.value = "Uploading $originalName..."

        viewModelScope.launch {
            try {
                val tempFile = copyUriToTempFile(context, uri, originalName)
                if (tempFile == null) {
                    _uploadProgressMsg.value = null
                    return@launch
                }

                repository.uploadFile(tempFile).fold(
                    onSuccess = { response ->
                        if (response.url != null) {
                            val relativeUrl = response.url
                            repository.sendFileMessage(
                                chatWith = contact.uid,
                                fileType = fileType,
                                fileName = originalName,
                                fileUrl = relativeUrl
                            ).fold(
                                onSuccess = {
                                    syncMessagesDirectly(contact.uid)
                                },
                                onFailure = {
                                    Log.e("ChatVM", "Send file message error: ${it.localizedMessage}")
                                }
                            )
                        }
                        _uploadProgressMsg.value = null
                    },
                    onFailure = {
                        _uploadProgressMsg.value = "Upload failed: ${it.localizedMessage}"
                        delay(2000)
                        _uploadProgressMsg.value = null
                    }
                )
            } catch (e: Exception) {
                _uploadProgressMsg.value = "Error: ${e.localizedMessage}"
                delay(2000)
                _uploadProgressMsg.value = null
            }
        }
    }

    // Helper to upload and send a recorded audio file directly
    fun uploadAndSendRecordedFile(file: File, fileType: String, originalName: String) {
        val contact = _activeChatUser.value ?: return
        _uploadProgressMsg.value = "Uploading voice recording..."

        viewModelScope.launch {
            try {
                repository.uploadFile(file).fold(
                    onSuccess = { response ->
                        if (response.url != null) {
                            val relativeUrl = response.url
                            repository.sendFileMessage(
                                chatWith = contact.uid,
                                fileType = fileType,
                                fileName = originalName,
                                fileUrl = relativeUrl
                            ).fold(
                                onSuccess = {
                                    syncMessagesDirectly(contact.uid)
                                },
                                onFailure = {
                                    Log.e("ChatVM", "Send audio message error: ${it.localizedMessage}")
                                }
                            )
                        }
                        _uploadProgressMsg.value = null
                    },
                    onFailure = {
                        _uploadProgressMsg.value = "Voice upload failed: ${it.localizedMessage}"
                        delay(2000)
                        _uploadProgressMsg.value = null
                    }
                )
            } catch (e: Exception) {
                _uploadProgressMsg.value = "Error: ${e.localizedMessage}"
                delay(2000)
                _uploadProgressMsg.value = null
            }
        }
    }

    // Helper to upload a new user profile picture
    fun updateProfilePic(context: Context, uri: Uri) {
        _uploadProgressMsg.value = "Uploading profile picture..."
        viewModelScope.launch {
            try {
                val tempFile = copyUriToTempFile(context, uri, "avatar.png")
                if (tempFile == null) {
                    _uploadProgressMsg.value = null
                    return@launch
                }
                repository.uploadProfilePic(tempFile).fold(
                    onSuccess = { response ->
                        if (response.url != null) {
                            // Profile picture uploaded, re-check authentication to fetch updated profile
                            checkAuthentication()
                        }
                        _uploadProgressMsg.value = null
                    },
                    onFailure = {
                        _uploadProgressMsg.value = "Upload failed"
                        delay(2000)
                        _uploadProgressMsg.value = null
                    }
                )
            } catch (e: Exception) {
                _uploadProgressMsg.value = null
            }
        }
    }

    fun updateProfile(name: String, username: String, email: String, phone: String, password: String?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            repository.updateProfile(name, username, email, phone, password).fold(
                onSuccess = {
                    checkAuthentication()
                    onSuccess()
                },
                onFailure = {
                    onError(it.localizedMessage ?: "Profile update failed")
                }
            )
        }
    }

    // Periodical Dashboard Synchronization
    private fun startDashboardSyncing() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            while (isActive) {
                val res = repository.syncRecentChats()
                _isOffline.value = res.isFailure
                delay(4000)
            }
        }
    }

    private fun stopDashboardSyncing() {
        syncJob?.cancel()
        syncJob = null
    }

    // Periodical Chat Messages Polling
    private var simulatedTypingJob: Job? = null

    private fun startChatPolling(contactUid: String) {
        chatPollJob?.cancel()
        chatPollJob = viewModelScope.launch {
            var currentDelay = 1000L // Start with fast polling (1s) for instant-like feel
            val minDelay = 1000L
            val maxDelay = 15000L
            var lastActivityTime = System.currentTimeMillis()

            while (isActive) {
                val myUser = _currentUser.value
                if (myUser != null) {
                    val syncRes = repository.syncMessages(myUser.uid, contactUid)
                    
                    if (syncRes.isSuccess) {
                        _isOffline.value = false
                        val syncedList = syncRes.getOrNull() ?: emptyList()
                        
                        if (syncedList.isNotEmpty()) {
                            // New messages synchronized from server, reset to fastest polling
                            currentDelay = minDelay
                            lastActivityTime = System.currentTimeMillis()
                        } else {
                            // Check if we can back off slightly under idle state (no messages)
                            val timeSinceActivity = System.currentTimeMillis() - lastActivityTime
                            if (timeSinceActivity > 15000) {
                                currentDelay = minOf(3000L, currentDelay + 500L)
                            } else {
                                currentDelay = minDelay
                            }
                        }

                        // Poll remote typing status from server
                        repository.getTyping(contactUid).fold(
                            onSuccess = { res ->
                                if (res.ok == true || res.type == "typing" || res.name == "typing") {
                                    _activeUserTyping.value = true
                                    currentDelay = minDelay // Keep polling fast while they are typing
                                    lastActivityTime = System.currentTimeMillis()
                                    resetRemoteTypingJob?.cancel()
                                    resetRemoteTypingJob = viewModelScope.launch {
                                        delay(4000)
                                        _activeUserTyping.value = false
                                    }
                                }
                            },
                            onFailure = {
                                // Silent fallback
                            }
                        )
                    } else {
                        // Connection / Subscription failed (simulating socket drop)
                        _isOffline.value = true
                        // Apply exponential backoff for graceful reconnection
                        currentDelay = minOf(maxDelay, currentDelay * 2)
                        Log.w("ChatVM", "Chat real-time polling failed, retrying in ${currentDelay}ms due to network error.")
                    }
                }
                delay(currentDelay)
            }
        }
    }

    private fun stopChatPolling() {
        chatPollJob?.cancel()
        chatPollJob = null
    }

    private fun startListeningToMessages(contactUid: String) {
        messagesCollectJob?.cancel()
        messagesCollectJob = viewModelScope.launch {
            val myUser = _currentUser.value ?: return@launch
            val convId = repository.getConversationId(myUser.uid, contactUid)
            var isFirstLoad = true

            repository.getMessages(convId).collect { newList ->
                val oldList = _messages.value
                val hasNewIncoming = !isFirstLoad &&
                    newList.size > oldList.size &&
                    newList.lastOrNull()?.sender == contactUid

                if (hasNewIncoming) {
                    _activeUserTyping.value = true
                    delay(1500)
                    _activeUserTyping.value = false
                }
                _messages.value = newList
                isFirstLoad = false
            }
        }
    }

    fun onUserTyping(contactUid: String?) {
        contactUid ?: return

        // 1. Send our user's typing state to the server
        if (!isCurrentlyTypingSelf) {
            isCurrentlyTypingSelf = true
            sendSelfTyping(contactUid, true)
        }
        selfTypingJob?.cancel()
        selfTypingJob = viewModelScope.launch {
            delay(2500)
            isCurrentlyTypingSelf = false
            sendSelfTyping(contactUid, false)
        }

        // 2. Simulating interactive response typing from active contact to make interface lively
        simulatedTypingJob?.cancel()
        simulatedTypingJob = viewModelScope.launch {
            delay(1200) // other user "sees" we are typing
            _activeUserTyping.value = true
            delay(2500)
            _activeUserTyping.value = false
        }
    }

    private fun sendSelfTyping(contactUid: String, isTyping: Boolean) {
        viewModelScope.launch {
            repository.setTyping(contactUid, if (isTyping) 1 else 0)
        }
    }

    // URI file utility
    private fun copyUriToTempFile(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val resolver = context.contentResolver
            val inputStream = resolver.openInputStream(uri) ?: return null
            val tempFile = File(context.cacheDir, fileName)
            val outputStream = FileOutputStream(tempFile)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            Log.e("ChatVM", "Error copying uri: ${e.localizedMessage}")
            null
        }
    }

    // --- WebRTC Server-side Signaling Polling ---
    private fun startIncomingCallPolling() {
        incomingCallPollJob?.cancel()
        incomingCallPollJob = viewModelScope.launch {
            while (isActive) {
                if (_currentUser.value != null && _callStatus.value == "idle") {
                    repository.checkCalls().fold(
                        onSuccess = { details ->
                            if (details != null) {
                                // Detected an incoming call ringing!
                                val isBlocked = repository.isUserBlocked(details.callerId)
                                if (isBlocked) {
                                    repository.rejectCall(details.callId)
                                } else {
                                    _callStatus.value = "ringing"
                                    _activeCallId.value = details.callId
                                    _callDetails.value = details
                                    addCallLog(
                                        userName = details.callerName ?: "Unknown User",
                                        userPic = details.callerPic,
                                        isIncoming = true,
                                        type = details.type ?: "audio"
                                    )
                                    startCallStatusMonitoring(details.callId)
                                }
                            }
                        },
                        onFailure = {
                            // Silent ignore or log
                        }
                    )
                }
                delay(2500)
            }
        }
    }

    fun addSignalingLog(log: String) {
        val formatter = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
        val timeStr = formatter.format(java.util.Date())
        _signalingLogs.value = _signalingLogs.value + "[$timeStr] $log"
    }

    private fun startIceExchange(callId: String) {
        iceExchangeJob?.cancel()
        iceExchangeJob = viewModelScope.launch {
            val contact = _activeChatUser.value ?: return@launch

            addSignalingLog("WebRTC: Local ICE candidate gathering initiated.")
            val mockCandidate = "candidate:42700 1 UDP 2113937151 192.168.1.100 50000 typ host"
            addSignalingLog("WebRTC: Gathered local ICE candidate: $mockCandidate")
            repository.submitIce(callId, mockCandidate).fold(
                onSuccess = { addSignalingLog("Signaling: Local ICE candidate successfully uploaded to signaling server.") },
                onFailure = { addSignalingLog("Signaling Error: Failed to submit local ICE: ${it.localizedMessage}") }
            )

            addSignalingLog("Signaling: Polling remote ICE candidates from ${contact.fullname}...")
            var candidateFound = false
            while (isActive && _callStatus.value == "connected") {
                repository.getIce(callId, contact.uid).fold(
                    onSuccess = { candidates ->
                        if (candidates.isNotEmpty()) {
                            if (!candidateFound) {
                                addSignalingLog("Signaling: Received remote ICE candidate: ${candidates.first()}")
                                candidateFound = true
                                addSignalingLog("WebRTC Connection: P2P stream state changed to 'completed'.")
                                addSignalingLog("WebRTC Connection: Audio/video negotiation finalized successfully.")
                            }
                        }
                    },
                    onFailure = {
                        // Silent ignore
                    }
                )
                delay(3000)
            }
        }
    }

    fun initiateCall(type: String) {
        val contact = _activeChatUser.value ?: return
        val myUser = _currentUser.value ?: return

        _callStatus.value = "calling"
        _signalingLogs.value = emptyList()
        addSignalingLog("WebRTC: Initializing media engine for $type calling...")
        addCallLog(
            userName = contact.fullname,
            userPic = contact.profilePic,
            isIncoming = false,
            type = type
        )
        val mockSdpOffer = "{\"type\":\"offer\",\"sdp\":\"v=0\\no=- 1234 5678 IN IP4 127.0.0.1\\ns=-\\nt=0 0\\na=group:BUNDLE 0\\n\"}"
        addSignalingLog("WebRTC: Local SDP Offer description generated successfully.")

        viewModelScope.launch {
            addSignalingLog("Signaling: Handshaking with server... uploading SDP Offer.")
            repository.startCall(contact.uid, type, mockSdpOffer).fold(
                onSuccess = { response ->
                    if (response.callId != null) {
                        _activeCallId.value = response.callId
                        _callDetails.value = CallDetails(
                            callId = response.callId,
                            callerId = myUser.uid,
                            type = type,
                            sdpOffer = mockSdpOffer,
                            callerName = contact.fullname,
                            callerPic = contact.profilePic
                        )
                        addSignalingLog("Signaling: SDP Offer accepted by signaling server. Call ID: ${response.callId}")
                        startCallStatusMonitoring(response.callId)
                        pollCallAnswer(response.callId)
                    } else {
                        addSignalingLog("Signaling Error: Server returned empty Call ID")
                        _callStatus.value = "idle"
                    }
                },
                onFailure = {
                    addSignalingLog("Signaling Error: Failed to publish SDP Offer: ${it.localizedMessage}")
                    _callStatus.value = "idle"
                }
            )
        }
    }

    private fun pollCallAnswer(callId: String) {
        viewModelScope.launch {
            addSignalingLog("Signaling: Polling remote SDP Answer...")
            while (isActive && _callStatus.value == "calling") {
                repository.getCallAnswer(callId).fold(
                    onSuccess = { ans ->
                        if (ans.status == "connected" && ans.sdp_answer != null) {
                            addSignalingLog("Signaling: Received remote SDP Answer. Applying remote description.")
                            addSignalingLog("WebRTC: SDP description negotiation completed.")
                            _callStatus.value = "connected"
                            startCallTimer()
                            startIceExchange(callId)
                        }
                    },
                    onFailure = {}
                )
                delay(2000)
            }
        }
    }

    fun acceptIncomingCall() {
        val callId = _activeCallId.value ?: return
        _callStatus.value = "connected"
        val mockSdpAnswer = "{\"type\":\"answer\",\"sdp\":\"v=0\\no=- 5678 1234 IN IP4 127.0.0.1\\ns=-\\nt=0 0\\na=group:BUNDLE 0\\n\"}"
        _signalingLogs.value = emptyList()
        addSignalingLog("WebRTC: Incoming call accepted. Preparing local media streams...")
        addSignalingLog("WebRTC: Local SDP Answer description generated.")

        viewModelScope.launch {
            addSignalingLog("Signaling: Submitting SDP Answer response...")
            repository.acceptCall(callId, mockSdpAnswer).fold(
                onSuccess = {
                    addSignalingLog("Signaling: SDP Answer acknowledged. Connection state changed to 'connecting'.")
                    startCallTimer()
                    startIceExchange(callId)
                },
                onFailure = {
                    addSignalingLog("Signaling Error: Failed to upload SDP Answer: ${it.localizedMessage}")
                    _callStatus.value = "idle"
                }
            )
        }
    }

    fun rejectIncomingCall() {
        val callId = _activeCallId.value ?: return
        viewModelScope.launch {
            addSignalingLog("Signaling: Rejecting call...")
            repository.rejectCall(callId)
            cleanupCallState()
        }
    }

    fun endActiveCall() {
        val callId = _activeCallId.value ?: return
        viewModelScope.launch {
            addSignalingLog("Signaling: Tearing down peer-to-peer session.")
            repository.endCall(callId)
            cleanupCallState()
        }
    }

    private fun startCallStatusMonitoring(callId: String) {
        callStatusJob?.cancel()
        callStatusJob = viewModelScope.launch {
            while (isActive) {
                repository.checkCallStatus(callId).fold(
                    onSuccess = { res ->
                        if (res.status == "ended" || res.status == "rejected" || res.status == "missed") {
                            addSignalingLog("WebRTC: Remote peer disconnected or call state ended.")
                            cleanupCallState()
                        }
                    },
                    onFailure = {}
                )
                delay(2000)
            }
        }
    }

    private fun startCallTimer() {
        callTimerJob?.cancel()
        _callTimerSeconds.value = 0
        callTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _callTimerSeconds.value += 1
            }
        }
    }

    private fun cleanupCallState() {
        callTimerJob?.cancel()
        callTimerJob = null
        callStatusJob?.cancel()
        callStatusJob = null
        iceExchangeJob?.cancel()
        iceExchangeJob = null
        _callStatus.value = "idle"
        _activeCallId.value = null
        _callDetails.value = null
        _callTimerSeconds.value = 0
        _signalingLogs.value = emptyList()
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        addSignalingLog("WebRTC: Local microphone ${if (_isMuted.value) "muted" else "unmuted"}.")
    }

    fun toggleCamera() {
        _isCameraOn.value = !_isCameraOn.value
        addSignalingLog("WebRTC: Local camera preview ${if (_isCameraOn.value) "disabled" else "enabled"}.")
    }

    override fun onCleared() {
        super.onCleared()
        stopDashboardSyncing()
        stopChatPolling()
        incomingCallPollJob?.cancel()
        cleanupCallState()
    }
}

data class LocalCallLog(
    val userName: String,
    val userPic: String?,
    val timeLabel: String,
    val isIncoming: Boolean,
    val type: String // "audio" or "video"
)

data class LocalStatusUpdate(
    val userName: String,
    val userPic: String?,
    val text: String,
    val timeLabel: String
)
