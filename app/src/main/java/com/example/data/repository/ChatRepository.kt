package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.api.ApiService
import com.example.data.api.RetrofitClient
import com.example.data.api.SupabaseBackend
import com.example.data.api.TwilioService
import com.example.data.crypto.EncryptionHelper
import com.example.data.local.*
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import retrofit2.HttpException
import java.io.File

class ChatRepository(
    private val context: Context,
    private val apiService: ApiService = RetrofitClient.apiService,
    private val chatDao: ChatDao
) {
    val supabase = SupabaseBackend(context)

    private fun getErrorMessage(throwable: Throwable): String {
        if (throwable is HttpException) {
            try {
                val errorBody = throwable.response()?.errorBody()?.string()
                if (!errorBody.isNullOrEmpty()) {
                    val json = JSONObject(errorBody)
                    if (json.has("error")) {
                        return json.getString("error")
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatRepository", "Error parsing error body", e)
            }
        }
        return throwable.localizedMessage ?: "Unknown error"
    }

    // Local Self User Cache
    suspend fun getCachedSelfUser(): CachedSelfUser? = withContext(Dispatchers.IO) {
        chatDao.getCachedSelfUser()
    }

    suspend fun saveSelfUser(user: CachedSelfUser) = withContext(Dispatchers.IO) {
        chatDao.clearSelfUser()
        chatDao.insertSelfUser(user)
    }

    suspend fun clearSelfUser() = withContext(Dispatchers.IO) {
        chatDao.clearSelfUser()
    }

    suspend fun insertLocalChat(chat: CachedChatUser) = withContext(Dispatchers.IO) {
        chatDao.insertRecentChats(listOf(chat))
    }

    // Local DB Observables
    val recentChats: Flow<List<RecentChat>> = chatDao.getRecentChats().map { list ->
        list.map { RecentChat(uid = it.uid, fullname = it.fullname, username = it.username, profilePic = it.profilePic, unread = it.unread) }
    }

    val allMessages: Flow<List<Message>> = chatDao.getAllMessages().map { list ->
        list.map { Message(id = it.id, sender = it.sender, text = it.text, type = it.type, fileUrl = it.fileUrl, fileName = it.fileName, seen = it.seen, time = it.time, conversationId = it.conversationId) }
    }

    val allReactions: Flow<List<LocalMessageReaction>> = chatDao.getAllReactions()

    suspend fun saveReaction(messageId: Int, reaction: String) = withContext(Dispatchers.IO) {
        chatDao.insertReaction(LocalMessageReaction(messageId, reaction))
    }

    suspend fun removeReaction(messageId: Int) = withContext(Dispatchers.IO) {
        chatDao.deleteReaction(messageId)
    }

    fun getMessages(conversationId: String): Flow<List<Message>> =
        chatDao.getMessagesForConversation(conversationId).map { list ->
            list.map { Message(id = it.id, sender = it.sender, text = it.text, type = it.type, fileUrl = it.fileUrl, fileName = it.fileName, seen = it.seen, time = it.time, conversationId = it.conversationId) }
        }

    // Helper to calculate conversationId
    fun getConversationId(myUid: String, contactUid: String): String {
        return if (myUid < contactUid) "${myUid}_${contactUid}" else "${contactUid}_${myUid}"
    }

    // Network Sync Operations
    suspend fun syncRecentChats() = withContext(Dispatchers.IO) {
        try {
            val remote = if (supabase.isEnabled()) {
                val selfUser = chatDao.getCachedSelfUser()
                val myUid = selfUser?.uid ?: ""
                if (myUid.isNotEmpty()) {
                    supabase.getRecentChats(myUid).getOrThrow()
                } else {
                    emptyList()
                }
            } else {
                apiService.getRecentChats()
            }
            val cached = remote.map {
                CachedChatUser(
                    uid = it.uid,
                    fullname = it.fullname,
                    username = it.username,
                    profilePic = it.avatarUrl,
                    unread = it.unread
                )
            }
            chatDao.clearRecentChats()
            chatDao.insertRecentChats(cached)
            Result.success(remote)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncMessages(myUid: String, contactUid: String, after: Int = 0) = withContext(Dispatchers.IO) {
        try {
            val remote = if (supabase.isEnabled()) {
                val convId = getConversationId(myUid, contactUid)
                supabase.getMessages(convId, after).getOrThrow()
            } else {
                apiService.getMessages(chatWith = contactUid, after = after)
            }
            
            // 1. Process key exchange control messages first to ensure keys are stored before decrypting
            remote.forEach { msg ->
                val text = msg.text ?: ""
                if (text.startsWith("__E2EE_KEY_EXCH__:")) {
                    val parts = text.split(":")
                    if (parts.size >= 2) {
                        val pubKeyHex = parts[1]
                        EncryptionHelper.saveRemotePublicKey(context, msg.sender, pubKeyHex)
                        
                        // Silently respond with our own public key if we haven't already or to confirm
                        try {
                            val myKeyPair = EncryptionHelper.getOrCreateKeyPair(context, myUid)
                            val myPubKeyHex = EncryptionHelper.bytesToHex(myKeyPair.public.encoded)
                            if (supabase.isEnabled()) {
                                supabase.sendMessage(myUid = myUid, chatWith = msg.sender, text = "__E2EE_KEY_EXCH_RESP__:$myPubKeyHex")
                            } else {
                                apiService.sendMessage(text = "__E2EE_KEY_EXCH_RESP__:$myPubKeyHex", chatWith = msg.sender)
                            }
                        } catch (e: Exception) {
                            Log.e("ChatRepo", "Failed to auto-respond to key exchange", e)
                        }
                    }
                } else if (text.startsWith("__E2EE_KEY_EXCH_RESP__:")) {
                    val parts = text.split(":")
                    if (parts.size >= 2) {
                        val pubKeyHex = parts[1]
                        EncryptionHelper.saveRemotePublicKey(context, msg.sender, pubKeyHex)
                    }
                }
            }

            val blockedUids = chatDao.getBlockedUserIds().toSet()
            val convId = getConversationId(myUid, contactUid)
            
            // 2. Filter out control messages and decrypt any encrypted messages
            val cached = remote.filter { msg ->
                val text = msg.text ?: ""
                !text.startsWith("__E2EE_KEY_EXCH__:") && 
                !text.startsWith("__E2EE_KEY_EXCH_RESP__:") &&
                !blockedUids.contains(msg.sender)
            }.map { msg ->
                val text = msg.text ?: ""
                val decryptedText = if (text.startsWith("__E2EE_ENC__:")) {
                    try {
                        val payload = text.substring("__E2EE_ENC__:".length)
                        val myKeyPair = EncryptionHelper.getOrCreateKeyPair(context, myUid)
                        val remotePubKey = EncryptionHelper.getRemotePublicKey(context, contactUid)
                        if (remotePubKey != null) {
                            val secretKey = EncryptionHelper.deriveSharedKey(myKeyPair.private, remotePubKey)
                            EncryptionHelper.decrypt(payload, secretKey)
                        } else {
                            "🔐 [Encrypted Message] Waiting for secure key exchange..."
                        }
                    } catch (e: Exception) {
                        "🔒 [Encrypted Message] Decryption Failed"
                    }
                } else {
                    text
                }

                CachedMessage(
                    id = msg.id,
                    conversationId = convId,
                    sender = msg.sender,
                    text = decryptedText,
                    type = msg.type,
                    fileUrl = msg.fileUrl,
                    fileName = msg.fileName,
                    seen = msg.seen,
                    time = msg.time
                )
            }

            if (after == 0) {
                chatDao.syncConversationMessages(convId, cached)
            } else {
                chatDao.insertMessages(cached)
            }
            Result.success(remote)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Auth
    suspend fun checkAuth(): Result<AuthState> = withContext(Dispatchers.IO) {
        try {
            val auth = if (supabase.isEnabled()) {
                supabase.checkAuth().getOrThrow()
            } else {
                apiService.checkAuth()
            }
            if (auth.authenticated && auth.uid != null) {
                chatDao.clearSelfUser()
                chatDao.insertSelfUser(
                    CachedSelfUser(
                        uid = auth.uid,
                        fullname = auth.fullname ?: "",
                        username = auth.username ?: "",
                        email = auth.email,
                        phone = auth.phone,
                        profilePic = auth.profilePic
                    )
                )
            }
            Result.success(auth)
        } catch (e: Exception) {
            val cached = chatDao.getCachedSelfUser()
            if (cached != null) {
                Result.success(
                    AuthState(
                        authenticated = true,
                        uid = cached.uid,
                        fullname = cached.fullname,
                        username = cached.username,
                        email = cached.email,
                        phone = cached.phone,
                        profilePic = cached.profilePic
                    )
                )
            } else {
                Result.failure(Exception(getErrorMessage(e)))
            }
        }
    }


    suspend fun register(name: String, username: String, pass: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            val user = if (supabase.isEnabled()) {
                supabase.signUp(pass = pass, fullname = name, username = username).getOrThrow()
            } else {
                apiService.register(name = name, username = username, password = pass)
            }
            chatDao.clearSelfUser()
            chatDao.insertSelfUser(
                CachedSelfUser(
                    uid = user.uid,
                    fullname = user.fullname,
                    username = user.username,
                    email = user.email,
                    phone = user.phone,
                    profilePic = user.profilePic
                )
            )
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(Exception(getErrorMessage(e)))
        }
    }

    suspend fun logout(): Result<GeneralResponse> = withContext(Dispatchers.IO) {
        try {
            val res = if (supabase.isEnabled()) {
                supabase.logout().getOrThrow()
            } else {
                apiService.logout()
            }
            RetrofitClient.cookieJar.clear()
            chatDao.clearSelfUser()
            chatDao.clearRecentChats()
            chatDao.clearAllMessages()
            Result.success(res)
        } catch (e: Exception) {
            RetrofitClient.cookieJar.clear()
            chatDao.clearSelfUser()
            chatDao.clearRecentChats()
            chatDao.clearAllMessages()
            Result.success(GeneralResponse(ok = true))
        }
    }

    // Search Users
    suspend fun searchUsers(q: String): Result<List<SimpleUser>> = withContext(Dispatchers.IO) {
        try {
            val res = if (supabase.isEnabled()) {
                supabase.searchUsers(q).getOrThrow()
            } else {
                apiService.searchUsers(query = q)
            }
            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Sending Messaging
    suspend fun sendMessage(myUid: String, text: String, chatWith: String): Result<GeneralResponse> = withContext(Dispatchers.IO) {
        val convId = getConversationId(myUid, chatWith)
        val tempId = -(System.currentTimeMillis() % 100000000).toInt()
        val tempMessage = CachedMessage(
            id = tempId,
            conversationId = convId,
            sender = myUid,
            text = text,
            type = "text",
            fileUrl = null,
            fileName = null,
            seen = false,
            time = System.currentTimeMillis()
        )
        // Cache locally first for instant offline visual feedback
        chatDao.insertMessages(listOf(tempMessage))
        try {
            val myKeyPair = EncryptionHelper.getOrCreateKeyPair(context, myUid)
            val remotePubKey = EncryptionHelper.getRemotePublicKey(context, chatWith)
            val payloadToSend = if (remotePubKey != null) {
                val secretKey = EncryptionHelper.deriveSharedKey(myKeyPair.private, remotePubKey)
                val encryptedText = EncryptionHelper.encrypt(text, secretKey)
                "__E2EE_ENC__:$encryptedText"
            } else {
                text
            }
            val res = if (supabase.isEnabled()) {
                supabase.sendMessage(myUid = myUid, chatWith = chatWith, text = payloadToSend).getOrThrow()
            } else {
                apiService.sendMessage(text = payloadToSend, chatWith = chatWith)
            }
            // Trigger a direct sync to pull down the newly sent message from the server first
            try {
                syncMessages(myUid, chatWith)
            } catch (ex: Exception) {
                Log.e("ChatRepo", "Failed to sync messages immediately after sending: ${ex.localizedMessage}")
            }
            // Now we can safely delete the temporary message without any visual gap/flicker
            chatDao.deleteMessageById(tempId)
            Result.success(res)
        } catch (e: Exception) {
            // Keep the cached message so it is shown as sent offline!
            Result.failure(e)
        }
    }

    suspend fun sendRawMessage(text: String, chatWith: String): Result<GeneralResponse> = withContext(Dispatchers.IO) {
        try {
            val selfUser = chatDao.getCachedSelfUser()
            val myUid = selfUser?.uid ?: ""
            val res = if (supabase.isEnabled() && myUid.isNotEmpty()) {
                supabase.sendMessage(myUid = myUid, chatWith = chatWith, text = text).getOrThrow()
            } else {
                apiService.sendMessage(text = text, chatWith = chatWith)
            }
            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendFileMessage(chatWith: String, fileType: String, fileName: String, fileUrl: String): Result<GeneralResponse> = withContext(Dispatchers.IO) {
        val selfUser = chatDao.getCachedSelfUser()
        val myUid = selfUser?.uid ?: ""
        val tempId = -(System.currentTimeMillis() % 100000000).toInt()
        if (myUid.isNotEmpty()) {
            val convId = getConversationId(myUid, chatWith)
            val tempMessage = CachedMessage(
                id = tempId,
                conversationId = convId,
                sender = myUid,
                text = "Sent a $fileType: $fileName",
                type = fileType,
                fileUrl = fileUrl,
                fileName = fileName,
                seen = false,
                time = System.currentTimeMillis()
            )
            chatDao.insertMessages(listOf(tempMessage))
        }
        try {
            val res = if (supabase.isEnabled() && myUid.isNotEmpty()) {
                supabase.sendFileMessage(myUid = myUid, chatWith = chatWith, fileType = fileType, fileName = fileName, fileUrl = fileUrl).getOrThrow()
            } else {
                apiService.sendFileMessage(chatWith = chatWith, fileType = fileType, fileName = fileName, fileUrl = fileUrl)
            }
            if (myUid.isNotEmpty()) {
                // Synchronize immediately to fetch official remote message
                try {
                    syncMessages(myUid, chatWith)
                } catch (ex: Exception) {
                    Log.e("ChatRepo", "Failed to sync messages immediately after sending file: ${ex.localizedMessage}")
                }
                chatDao.deleteMessageById(tempId)
            }
            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markSeen(chatWith: String): Result<GeneralResponse> = withContext(Dispatchers.IO) {
        try {
            val selfUser = chatDao.getCachedSelfUser()
            val myUid = selfUser?.uid ?: ""
            val res = if (supabase.isEnabled() && myUid.isNotEmpty()) {
                supabase.markSeen(myUid = myUid, chatWith = chatWith).getOrThrow()
            } else {
                apiService.markSeen(chatWith = chatWith)
            }
            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setTyping(chatWith: String, typing: Int): Result<GeneralResponse> = withContext(Dispatchers.IO) {
        try {
            val selfUser = chatDao.getCachedSelfUser()
            val myUid = selfUser?.uid ?: ""
            val res = if (supabase.isEnabled() && myUid.isNotEmpty()) {
                supabase.setTyping(myUid = myUid, chatWith = chatWith, typing = typing).getOrThrow()
            } else {
                apiService.setTyping(chatWith = chatWith, typing = typing)
            }
            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTyping(chatWith: String): Result<GeneralResponse> = withContext(Dispatchers.IO) {
        try {
            val selfUser = chatDao.getCachedSelfUser()
            val myUid = selfUser?.uid ?: ""
            val res = if (supabase.isEnabled() && myUid.isNotEmpty()) {
                supabase.getTyping(myUid = myUid, chatWith = chatWith).getOrThrow()
            } else {
                apiService.getTyping(chatWith = chatWith)
            }
            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(name: String, username: String, email: String, phone: String, pass: String?): Result<GeneralResponse> = withContext(Dispatchers.IO) {
        try {
            val selfUser = chatDao.getCachedSelfUser()
            val myUid = selfUser?.uid ?: ""
            val res = if (supabase.isEnabled() && myUid.isNotEmpty()) {
                supabase.updateProfile(myUid = myUid, name = name, username = username, email = email, phone = phone, pass = pass).getOrThrow()
            } else {
                apiService.updateProfile(name = name, username = username, email = email, phone = phone, password = pass)
            }
            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Upload Files
    suspend fun uploadFile(file: File): Result<GeneralResponse> = withContext(Dispatchers.IO) {
        try {
            val res = if (supabase.isEnabled()) {
                supabase.uploadFile(file, "chat-files").getOrThrow()
            } else {
                val requestFile = file.asRequestBody("multipart/form-data".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                apiService.uploadFile(file = body)
            }
            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadProfilePic(file: File): Result<GeneralResponse> = withContext(Dispatchers.IO) {
        try {
            val res = if (supabase.isEnabled()) {
                supabase.uploadFile(file, "avatars").getOrThrow()
            } else {
                val requestFile = file.asRequestBody("multipart/form-data".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                apiService.uploadProfilePic(file = body)
            }
            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUser(uid: String): Result<SimpleUser> = withContext(Dispatchers.IO) {
        try {
            val user = if (supabase.isEnabled()) {
                supabase.getUser(uid).getOrThrow()
            } else {
                apiService.getUser(uid = uid)
            }
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Call Actions
    suspend fun startCall(targetId: String, type: String, sdp: String): Result<StartCallResponse> = withContext(Dispatchers.IO) {
        try {
            val selfUser = chatDao.getCachedSelfUser()
            val myUid = selfUser?.uid ?: ""
            val res = if (supabase.isEnabled() && myUid.isNotEmpty()) {
                supabase.startCall(myUid = myUid, targetId = targetId, type = type, sdp = sdp).getOrThrow()
            } else {
                apiService.startCall(targetId = targetId, type = type, sdp = sdp)
            }
            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkCalls(): Result<CallDetails?> = withContext(Dispatchers.IO) {
        try {
            val selfUser = chatDao.getCachedSelfUser()
            val myUid = selfUser?.uid ?: ""
            val res = if (supabase.isEnabled() && myUid.isNotEmpty()) {
                supabase.checkCalls(myUid = myUid).getOrThrow()
            } else {
                apiService.checkCalls()
            }
            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptCall(callId: String, sdp: String): Result<GeneralResponse> = withContext(Dispatchers.IO) {
        try {
            val res = if (supabase.isEnabled()) {
                supabase.acceptCall(callId = callId, sdp = sdp).getOrThrow()
            } else {
                apiService.acceptCall(callId = callId, sdp = sdp)
            }
            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCallAnswer(callId: String): Result<CallAnswerResponse> = withContext(Dispatchers.IO) {
        try {
            val res = if (supabase.isEnabled()) {
                supabase.getCallAnswer(callId = callId).getOrThrow()
            } else {
                apiService.getCallAnswer(callId = callId)
            }
            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rejectCall(callId: String): Result<GeneralResponse> = withContext(Dispatchers.IO) {
        try {
            val res = if (supabase.isEnabled()) {
                supabase.rejectCall(callId = callId).getOrThrow()
            } else {
                apiService.rejectCall(callId = callId)
            }
            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun endCall(callId: String): Result<GeneralResponse> = withContext(Dispatchers.IO) {
        try {
            val res = if (supabase.isEnabled()) {
                supabase.endCall(callId = callId).getOrThrow()
            } else {
                apiService.endCall(callId = callId)
            }
            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkCallStatus(callId: String): Result<CallStatusResponse> = withContext(Dispatchers.IO) {
        try {
            val res = if (supabase.isEnabled()) {
                supabase.checkCallStatus(callId = callId).getOrThrow()
            } else {
                apiService.checkCallStatus(callId = callId)
            }
            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitIce(callId: String, candidate: String): Result<GeneralResponse> = withContext(Dispatchers.IO) {
        try {
            val selfUser = chatDao.getCachedSelfUser()
            val myUid = selfUser?.uid ?: ""
            val res = if (supabase.isEnabled() && myUid.isNotEmpty()) {
                supabase.submitIce(callId = callId, myUid = myUid, candidate = candidate).getOrThrow()
            } else {
                apiService.submitIce(callId = callId, candidate = candidate)
            }
            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getIce(callId: String, fromId: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val res = if (supabase.isEnabled()) {
                supabase.getIce(callId = callId, fromId = fromId).getOrThrow()
            } else {
                apiService.getIce(callId = callId, fromId = fromId)
            }
            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    val blockedUsers: Flow<List<BlockedUser>> = chatDao.getBlockedUsers()

    suspend fun blockUser(uid: String, fullname: String, username: String, profilePic: String?) = withContext(Dispatchers.IO) {
        chatDao.insertBlockedUser(BlockedUser(uid, fullname, username, profilePic))
    }

    suspend fun unblockUser(uid: String) = withContext(Dispatchers.IO) {
        chatDao.deleteBlockedUser(uid)
    }

    suspend fun isUserBlocked(uid: String): Boolean = withContext(Dispatchers.IO) {
        chatDao.isUserBlocked(uid)
    }

    suspend fun deleteMessage(id: Int) = withContext(Dispatchers.IO) {
        chatDao.deleteMessageById(id)
    }

    suspend fun deleteMessages(ids: List<Int>) = withContext(Dispatchers.IO) {
        chatDao.deleteMessagesByIds(ids)
    }
}
