package com.example.data.api

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.URLConnection
import java.util.concurrent.TimeUnit

class SupabaseBackend(private val context: Context) {
    private val prefs = context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Helper to get active URL
    fun getSupabaseUrl(): String {
        val saved = prefs.getString("supabase_url", "") ?: ""
        if (saved.isNotBlank()) return saved.trim().removeSuffix("/")
        // Fallback to BuildConfig if configured
        val defaultUrl = try { BuildConfig.SUPABASE_URL } catch (e: Exception) { "" }
        if (defaultUrl.isNotBlank() && defaultUrl != "YOUR_SUPABASE_URL") return defaultUrl.trim().removeSuffix("/")
        return ""
    }

    // Helper to get active Anon Key
    fun getSupabaseAnonKey(): String {
        val saved = prefs.getString("supabase_anon_key", "") ?: ""
        if (saved.isNotBlank()) return saved.trim()
        // Fallback to BuildConfig if configured
        val defaultKey = try { BuildConfig.SUPABASE_ANON_KEY } catch (e: Exception) { "" }
        if (defaultKey.isNotBlank() && defaultKey != "YOUR_SUPABASE_ANON_KEY") return defaultKey.trim()
        return ""
    }

    // Helper to get active Auth Token
    fun getAuthToken(): String? {
        return prefs.getString("supabase_token", null)
    }

    fun isConfigured(): Boolean {
        return getSupabaseUrl().isNotBlank() && getSupabaseAnonKey().isNotBlank()
    }

    fun isEnabled(): Boolean {
        return prefs.getBoolean("supabase_enabled", true) && isConfigured()
    }

    private fun makeRequest(
        url: String,
        method: String,
        body: RequestBody? = null,
        headers: Map<String, String> = emptyMap()
    ): Response {
        val sUrl = getSupabaseUrl()
        val sKey = getSupabaseAnonKey()
        if (sUrl.isBlank() || sKey.isBlank()) {
            throw IOException("Supabase is not configured yet. Please configure it in Settings.")
        }

        val requestUrl = if (url.startsWith("http")) url else "$sUrl$url"
        val builder = Request.Builder()
            .url(requestUrl)
            .method(method, body)
            .addHeader("apikey", sKey)

        val token = getAuthToken()
        if (token != null) {
            builder.addHeader("Authorization", "Bearer $token")
        } else {
            builder.addHeader("Authorization", "Bearer $sKey")
        }

        headers.forEach { (k, v) ->
            builder.header(k, v)
        }

        val request = builder.build()
        return client.newCall(request).execute()
    }

    fun signUp(email: String, pass: String, fullname: String, username: String, phone: String): Result<User> {
        return try {
            val signupData = mapOf(
                "fullname" to fullname,
                "username" to username,
                "phone" to phone
            )
            val jsonBody = mapOf(
                "email" to email,
                "password" to pass,
                "data" to signupData
            )
            val bodyString = moshi.adapter(Map::class.java).toJson(jsonBody)
            val requestBody = bodyString.toRequestBody("application/json".toMediaTypeOrNull())

            val response = makeRequest("/auth/v1/signup", "POST", requestBody)
            val respStr = response.body?.string() ?: ""
            response.close()

            if (!response.isSuccessful) {
                val errMsg = parseErrorMsg(respStr) ?: "Sign up failed: ${response.code}"
                return Result.failure(Exception(errMsg))
            }

            val json = org.json.JSONObject(respStr)
            val userObj = if (json.has("user")) json.getJSONObject("user") else json
            val uid = userObj.getString("id")
            
            val token = if (json.has("access_token")) json.getString("access_token") else null
            if (token != null) {
                prefs.edit().putString("supabase_token", token).apply()
            }

            // Insert Profile row directly in public.profiles table
            val profileBody = mapOf(
                "id" to uid,
                "fullname" to fullname,
                "username" to username,
                "email" to email,
                "phone" to phone
            )
            val profileStr = moshi.adapter(Map::class.java).toJson(profileBody)
            val profileReqBody = profileStr.toRequestBody("application/json".toMediaTypeOrNull())
            
            try {
                val profResp = makeRequest("/rest/v1/profiles", "POST", profileReqBody)
                profResp.close()
            } catch (e: Exception) {
                Log.e("Supabase", "Failed to insert profile row directly", e)
            }

            val user = User(
                uid = uid,
                fullname = fullname,
                username = username,
                email = email,
                phone = phone,
                profilePic = null
            )
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun login(email: String, pass: String): Result<User> {
        return try {
            val jsonBody = mapOf(
                "email" to email,
                "password" to pass
            )
            val bodyString = moshi.adapter(Map::class.java).toJson(jsonBody)
            val requestBody = bodyString.toRequestBody("application/json".toMediaTypeOrNull())

            val response = makeRequest("/auth/v1/token?grant_type=password", "POST", requestBody)
            val respStr = response.body?.string() ?: ""
            response.close()

            if (!response.isSuccessful) {
                val errMsg = parseErrorMsg(respStr) ?: "Login failed: ${response.code}"
                return Result.failure(Exception(errMsg))
            }

            val json = org.json.JSONObject(respStr)
            val token = json.getString("access_token")
            val userObj = json.getJSONObject("user")
            val uid = userObj.getString("id")

            prefs.edit()
                .putString("supabase_token", token)
                .putString("supabase_uid", uid)
                .apply()

            var fullname = ""
            var username = ""
            var phone = ""
            var profilePic: String? = null

            try {
                val profResp = makeRequest("/rest/v1/profiles?id=eq.$uid&select=*", "GET")
                val profStr = profResp.body?.string() ?: ""
                profResp.close()
                
                val array = org.json.JSONArray(profStr)
                if (array.length() > 0) {
                    val p = array.getJSONObject(0)
                    fullname = p.optString("fullname", "")
                    username = p.optString("username", "")
                    phone = p.optString("phone", "")
                    profilePic = if (p.isNull("profile_pic")) null else p.optString("profile_pic")
                }
            } catch (e: Exception) {
                Log.e("Supabase", "Failed to fetch profile metadata", e)
            }

            if (fullname.isBlank()) {
                val metadata = userObj.optJSONObject("user_metadata")
                fullname = metadata?.optString("fullname", "") ?: "User"
                username = metadata?.optString("username", "") ?: "user"
                phone = metadata?.optString("phone", "") ?: ""
            }

            val user = User(
                uid = uid,
                fullname = fullname,
                username = username,
                email = email,
                phone = phone,
                profilePic = profilePic
            )
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun checkAuth(): Result<AuthState> {
        val token = getAuthToken() ?: return Result.success(AuthState(authenticated = false))
        val uid = prefs.getString("supabase_uid", "") ?: ""
        if (uid.isBlank()) return Result.success(AuthState(authenticated = false))

        return try {
            val response = makeRequest("/auth/v1/user", "GET")
            val respStr = response.body?.string() ?: ""
            response.close()

            if (!response.isSuccessful) {
                prefs.edit().remove("supabase_token").remove("supabase_uid").apply()
                return Result.success(AuthState(authenticated = false))
            }

            var fullname = ""
            var username = ""
            var phone = ""
            var email = ""
            var profilePic: String? = null

            try {
                val profResp = makeRequest("/rest/v1/profiles?id=eq.$uid&select=*", "GET")
                val profStr = profResp.body?.string() ?: ""
                profResp.close()
                
                val array = org.json.JSONArray(profStr)
                if (array.length() > 0) {
                    val p = array.getJSONObject(0)
                    fullname = p.optString("fullname", "")
                    username = p.optString("username", "")
                    email = p.optString("email", "")
                    phone = p.optString("phone", "")
                    profilePic = if (p.isNull("profile_pic")) null else p.optString("profile_pic")
                }
            } catch (e: Exception) {
                Log.e("Supabase", "Failed to fetch auth profile details", e)
            }

            if (fullname.isBlank()) {
                val json = org.json.JSONObject(respStr)
                val metadata = json.optJSONObject("user_metadata")
                fullname = metadata?.optString("fullname", "User") ?: "User"
                username = metadata?.optString("username", "user") ?: "user"
                email = json.optString("email", "")
                phone = metadata?.optString("phone", "") ?: ""
            }

            Result.success(
                AuthState(
                    authenticated = true,
                    uid = uid,
                    fullname = fullname,
                    username = username,
                    email = email,
                    phone = phone,
                    profilePic = profilePic
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout(): Result<GeneralResponse> {
        try {
            val resp = makeRequest("/auth/v1/logout", "POST")
            resp.close()
        } catch (e: Exception) {
            // Log out locally anyway
        }
        prefs.edit()
            .remove("supabase_token")
            .remove("supabase_uid")
            .apply()
        return Result.success(GeneralResponse(ok = true))
    }

    fun searchUsers(query: String): Result<List<SimpleUser>> {
        return try {
            val response = makeRequest("/rest/v1/profiles?or=(fullname.ilike.*$query*,username.ilike.*$query*)&limit=15", "GET")
            val respStr = response.body?.string() ?: ""
            response.close()

            if (!response.isSuccessful) {
                return Result.failure(Exception("Search failed: ${response.code}"))
            }

            val list = mutableListOf<SimpleUser>()
            val array = org.json.JSONArray(respStr)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val uid = o.getString("id")
                val selfUid = prefs.getString("supabase_uid", "")
                if (uid == selfUid) continue

                val name = o.optString("fullname", "")
                val username = o.optString("username", "")
                val pic = if (o.isNull("profile_pic")) null else o.optString("profile_pic")

                list.add(SimpleUser(uid = uid, fullname = name, username = username, profilePic = pic))
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getRecentChats(myUid: String): Result<List<RecentChat>> {
        return try {
            val response = makeRequest("/rest/v1/messages?or=(sender.eq.$myUid,receiver.eq.$myUid)&order=time.desc&limit=150", "GET")
            val respStr = response.body?.string() ?: ""
            response.close()

            if (!response.isSuccessful) {
                return Result.failure(Exception("Failed to load recent chats: ${response.code}"))
            }

            val msgArray = org.json.JSONArray(respStr)
            val uids = LinkedHashSet<String>()
            for (i in 0 until msgArray.length()) {
                val m = msgArray.getJSONObject(i)
                val sender = m.getString("sender")
                val receiver = m.getString("receiver")
                if (sender != myUid) uids.add(sender)
                if (receiver != myUid) uids.add(receiver)
            }

            if (uids.isEmpty()) {
                return Result.success(emptyList())
            }

            val uidsParam = uids.joinToString(",")
            val profResp = makeRequest("/rest/v1/profiles?id=in.($uidsParam)", "GET")
            val profStr = profResp.body?.string() ?: ""
            profResp.close()

            val profArray = org.json.JSONArray(profStr)
            val profileMap = mutableMapOf<String, org.json.JSONObject>()
            for (i in 0 until profArray.length()) {
                val o = profArray.getJSONObject(i)
                profileMap[o.getString("id")] = o
            }

            val unreadCounts = mutableMapOf<String, Int>()
            for (i in 0 until msgArray.length()) {
                val m = msgArray.getJSONObject(i)
                val sender = m.getString("sender")
                val receiver = m.getString("receiver")
                val seen = m.optBoolean("seen", false)
                if (receiver == myUid && !seen) {
                    unreadCounts[sender] = (unreadCounts[sender] ?: 0) + 1
                }
            }

            val list = uids.mapNotNull { uid ->
                val pObj = profileMap[uid] ?: return@mapNotNull null
                val name = pObj.optString("fullname", "User")
                val uname = pObj.optString("username", "user")
                val pic = if (pObj.isNull("profile_pic")) null else pObj.optString("profile_pic")
                RecentChat(
                    uid = uid,
                    fullname = name,
                    username = uname,
                    profilePic = pic,
                    unread = unreadCounts[uid] ?: 0
                )
            }

            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getMessages(conversationId: String, afterId: Int = 0): Result<List<Message>> {
        return try {
            val query = if (afterId > 0) {
                "/rest/v1/messages?conversation_id=eq.$conversationId&id=gt.$afterId&order=time.asc"
            } else {
                "/rest/v1/messages?conversation_id=eq.$conversationId&order=time.asc"
            }
            val response = makeRequest(query, "GET")
            val respStr = response.body?.string() ?: ""
            response.close()

            if (!response.isSuccessful) {
                return Result.failure(Exception("Get messages error: ${response.code}"))
            }

            val list = mutableListOf<Message>()
            val array = org.json.JSONArray(respStr)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                list.add(
                    Message(
                        id = o.getInt("id"),
                        sender = o.getString("sender"),
                        text = if (o.isNull("text")) null else o.optString("text"),
                        type = o.getString("type"),
                        fileUrl = if (o.isNull("file_url")) null else o.optString("file_url"),
                        fileName = if (o.isNull("file_name")) null else o.optString("file_name"),
                        seen = o.optBoolean("seen", false),
                        time = o.getLong("time"),
                        conversationId = o.getString("conversation_id")
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sendMessage(myUid: String, chatWith: String, text: String): Result<GeneralResponse> {
        return try {
            val convId = if (myUid < chatWith) "${myUid}_${chatWith}" else "${chatWith}_${myUid}"
            val bodyMap = mapOf(
                "sender" to myUid,
                "receiver" to chatWith,
                "text" to text,
                "type" to "text",
                "seen" to false,
                "time" to System.currentTimeMillis(),
                "conversation_id" to convId
            )
            val bodyStr = moshi.adapter(Map::class.java).toJson(bodyMap)
            val requestBody = bodyStr.toRequestBody("application/json".toMediaTypeOrNull())

            val response = makeRequest("/rest/v1/messages", "POST", requestBody, mapOf("Prefer" to "return=representation"))
            val respStr = response.body?.string() ?: ""
            response.close()

            if (!response.isSuccessful) {
                return Result.failure(Exception("Failed to send message: ${response.code}"))
            }

            val array = org.json.JSONArray(respStr)
            val insertedId = if (array.length() > 0) array.getJSONObject(0).optInt("id", 0) else 0

            Result.success(GeneralResponse(ok = true, id = insertedId, time = System.currentTimeMillis()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sendFileMessage(myUid: String, chatWith: String, fileType: String, fileName: String, fileUrl: String): Result<GeneralResponse> {
        return try {
            val convId = if (myUid < chatWith) "${myUid}_${chatWith}" else "${chatWith}_${myUid}"
            val bodyMap = mapOf(
                "sender" to myUid,
                "receiver" to chatWith,
                "text" to "Sent a $fileType: $fileName",
                "type" to fileType,
                "file_url" to fileUrl,
                "file_name" to fileName,
                "seen" to false,
                "time" to System.currentTimeMillis(),
                "conversation_id" to convId
            )
            val bodyStr = moshi.adapter(Map::class.java).toJson(bodyMap)
            val requestBody = bodyStr.toRequestBody("application/json".toMediaTypeOrNull())

            val response = makeRequest("/rest/v1/messages", "POST", requestBody, mapOf("Prefer" to "return=representation"))
            val respStr = response.body?.string() ?: ""
            response.close()

            if (!response.isSuccessful) {
                return Result.failure(Exception("Failed to send file message: ${response.code}"))
            }

            val array = org.json.JSONArray(respStr)
            val insertedId = if (array.length() > 0) array.getJSONObject(0).optInt("id", 0) else 0

            Result.success(GeneralResponse(ok = true, id = insertedId, time = System.currentTimeMillis()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun markSeen(myUid: String, chatWith: String): Result<GeneralResponse> {
        return try {
            val bodyMap = mapOf("seen" to true)
            val bodyStr = moshi.adapter(Map::class.java).toJson(bodyMap)
            val requestBody = bodyStr.toRequestBody("application/json".toMediaTypeOrNull())

            val response = makeRequest("/rest/v1/messages?sender=eq.$chatWith&receiver=eq.$myUid&seen=eq.false", "PATCH", requestBody)
            response.close()

            Result.success(GeneralResponse(ok = true))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setTyping(myUid: String, chatWith: String, typing: Int): Result<GeneralResponse> {
        return try {
            val bodyMap = mapOf(
                "uid" to myUid,
                "chat_with" to chatWith,
                "typing" to typing,
                "updated_at" to "now()"
            )
            val bodyStr = moshi.adapter(Map::class.java).toJson(bodyMap)
            val requestBody = bodyStr.toRequestBody("application/json".toMediaTypeOrNull())

            val response = makeRequest("/rest/v1/typing_status", "POST", requestBody, mapOf("Prefer" to "resolution=merge-duplicates"))
            response.close()

            Result.success(GeneralResponse(ok = true))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getTyping(myUid: String, chatWith: String): Result<GeneralResponse> {
        return try {
            val response = makeRequest("/rest/v1/typing_status?uid=eq.$chatWith&chat_with=eq.$myUid", "GET")
            val respStr = response.body?.string() ?: ""
            response.close()

            val array = org.json.JSONArray(respStr)
            if (array.length() > 0) {
                val o = array.getJSONObject(0)
                val typing = o.optInt("typing", 0)
                Result.success(GeneralResponse(ok = typing == 1))
            } else {
                Result.success(GeneralResponse(ok = false))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun updateProfile(myUid: String, name: String, username: String, email: String, phone: String, pass: String?): Result<GeneralResponse> {
        return try {
            val bodyMap = mutableMapOf<String, Any>(
                "fullname" to name,
                "username" to username,
                "email" to email,
                "phone" to phone
            )
            val bodyStr = moshi.adapter(Map::class.java).toJson(bodyMap)
            val requestBody = bodyStr.toRequestBody("application/json".toMediaTypeOrNull())

            val response = makeRequest("/rest/v1/profiles?id=eq.$myUid", "PATCH", requestBody)
            response.close()

            if (!response.isSuccessful) {
                return Result.failure(Exception("Profile update failed: ${response.code}"))
            }

            Result.success(GeneralResponse(ok = true))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun uploadFile(file: File, bucket: String = "chat-files"): Result<GeneralResponse> {
        return try {
            val sUrl = getSupabaseUrl()
            val fileName = "file_${System.currentTimeMillis()}_${file.name}"
            val mimeType = URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
            val requestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())

            val uploadUrl = "/storage/v1/object/$bucket/$fileName"
            val response = makeRequest(uploadUrl, "POST", requestBody, mapOf("Content-Type" to mimeType))
            val respStr = response.body?.string() ?: ""
            response.close()

            if (!response.isSuccessful) {
                val errMsg = parseErrorMsg(respStr) ?: "Storage upload failed: ${response.code}"
                return Result.failure(Exception(errMsg))
            }

            val publicUrl = "$sUrl/storage/v1/object/public/$bucket/$fileName"
            Result.success(GeneralResponse(ok = true, url = publicUrl))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun startCall(myUid: String, targetId: String, type: String, sdp: String): Result<StartCallResponse> {
        return try {
            val bodyMap = mapOf(
                "caller_id" to myUid,
                "receiver_id" to targetId,
                "type" to type,
                "sdp_offer" to sdp,
                "status" to "pending"
            )
            val bodyStr = moshi.adapter(Map::class.java).toJson(bodyMap)
            val requestBody = bodyStr.toRequestBody("application/json".toMediaTypeOrNull())

            val response = makeRequest("/rest/v1/calls", "POST", requestBody, mapOf("Prefer" to "return=representation"))
            val respStr = response.body?.string() ?: ""
            response.close()

            val array = org.json.JSONArray(respStr)
            if (array.length() > 0) {
                val id = array.getJSONObject(0).getString("id")
                Result.success(StartCallResponse(callId = id))
            } else {
                Result.failure(Exception("Failed to start call in database"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun checkCalls(myUid: String): Result<CallDetails?> {
        return try {
            val response = makeRequest("/rest/v1/calls?receiver_id=eq.$myUid&status=eq.pending&order=created_at.desc&limit=1", "GET")
            val respStr = response.body?.string() ?: ""
            response.close()

            val array = org.json.JSONArray(respStr)
            if (array.length() > 0) {
                val callObj = array.getJSONObject(0)
                val callId = callObj.getString("id")
                val callerId = callObj.getString("caller_id")
                val type = callObj.getString("type")
                val sdpOffer = callObj.getString("sdp_offer")

                val callerProfResp = makeRequest("/rest/v1/profiles?id=eq.$callerId", "GET")
                val callerProfStr = callerProfResp.body?.string() ?: ""
                callerProfResp.close()

                var callerName = "Caller"
                var callerPic: String? = null
                val profArray = org.json.JSONArray(callerProfStr)
                if (profArray.length() > 0) {
                    val p = profArray.getJSONObject(0)
                    callerName = p.optString("fullname", "Caller")
                    callerPic = if (p.isNull("profile_pic")) null else p.optString("profile_pic")
                }

                Result.success(
                    CallDetails(
                        callId = callId,
                        callerId = callerId,
                        type = type,
                        sdpOffer = sdpOffer,
                        callerName = callerName,
                        callerPic = callerPic
                    )
                )
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun acceptCall(callId: String, sdp: String): Result<GeneralResponse> {
        return try {
            val bodyMap = mapOf(
                "sdp_answer" to sdp,
                "status" to "accepted"
            )
            val bodyStr = moshi.adapter(Map::class.java).toJson(bodyMap)
            val requestBody = bodyStr.toRequestBody("application/json".toMediaTypeOrNull())

            val response = makeRequest("/rest/v1/calls?id=eq.$callId", "PATCH", requestBody)
            response.close()

            Result.success(GeneralResponse(ok = true))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCallAnswer(callId: String): Result<CallAnswerResponse> {
        return try {
            val response = makeRequest("/rest/v1/calls?id=eq.$callId", "GET")
            val respStr = response.body?.string() ?: ""
            response.close()

            val array = org.json.JSONArray(respStr)
            if (array.length() > 0) {
                val callObj = array.getJSONObject(0)
                val status = callObj.optString("status", "")
                val sdpAnswer = if (callObj.isNull("sdp_answer")) null else callObj.optString("sdp_answer")
                Result.success(CallAnswerResponse(sdp_answer = sdpAnswer, status = status))
            } else {
                Result.failure(Exception("Call not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun rejectCall(callId: String): Result<GeneralResponse> {
        return try {
            val bodyMap = mapOf("status" to "rejected")
            val bodyStr = moshi.adapter(Map::class.java).toJson(bodyMap)
            val requestBody = bodyStr.toRequestBody("application/json".toMediaTypeOrNull())

            val response = makeRequest("/rest/v1/calls?id=eq.$callId", "PATCH", requestBody)
            response.close()

            Result.success(GeneralResponse(ok = true))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun endCall(callId: String): Result<GeneralResponse> {
        return try {
            val bodyMap = mapOf("status" to "ended")
            val bodyStr = moshi.adapter(Map::class.java).toJson(bodyMap)
            val requestBody = bodyStr.toRequestBody("application/json".toMediaTypeOrNull())

            val response = makeRequest("/rest/v1/calls?id=eq.$callId", "PATCH", requestBody)
            response.close()

            Result.success(GeneralResponse(ok = true))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun checkCallStatus(callId: String): Result<CallStatusResponse> {
        return try {
            val response = makeRequest("/rest/v1/calls?id=eq.$callId", "GET")
            val respStr = response.body?.string() ?: ""
            response.close()

            val array = org.json.JSONArray(respStr)
            if (array.length() > 0) {
                val status = array.getJSONObject(0).optString("status", "idle")
                Result.success(CallStatusResponse(status = status))
            } else {
                Result.success(CallStatusResponse(status = "ended"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun submitIce(callId: String, myUid: String, candidate: String): Result<GeneralResponse> {
        return try {
            val bodyMap = mapOf(
                "call_id" to callId,
                "from_id" to myUid,
                "candidate" to candidate
            )
            val bodyStr = moshi.adapter(Map::class.java).toJson(bodyMap)
            val requestBody = bodyStr.toRequestBody("application/json".toMediaTypeOrNull())

            val response = makeRequest("/rest/v1/ice_candidates", "POST", requestBody)
            response.close()

            Result.success(GeneralResponse(ok = true))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getIce(callId: String, fromId: String): Result<List<String>> {
        return try {
            val response = makeRequest("/rest/v1/ice_candidates?call_id=eq.$callId&from_id=eq.$fromId", "GET")
            val respStr = response.body?.string() ?: ""
            response.close()

            val list = mutableListOf<String>()
            val array = org.json.JSONArray(respStr)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                list.add(o.getString("candidate"))
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getUser(uid: String): Result<SimpleUser> {
        return try {
            val response = makeRequest("/rest/v1/profiles?id=eq.$uid", "GET")
            val respStr = response.body?.string() ?: ""
            response.close()

            val array = org.json.JSONArray(respStr)
            if (array.length() > 0) {
                val o = array.getJSONObject(0)
                val name = o.optString("fullname", "User")
                val uname = o.optString("username", "user")
                val pic = if (o.isNull("profile_pic")) null else o.optString("profile_pic")
                Result.success(SimpleUser(uid = uid, fullname = name, username = uname, profilePic = pic))
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseErrorMsg(jsonStr: String): String? {
        return try {
            val o = org.json.JSONObject(jsonStr)
            if (o.has("message")) o.getString("message")
            else if (o.has("msg")) o.getString("msg")
            else if (o.has("error_description")) o.getString("error_description")
            else null
        } catch (e: Exception) {
            null
        }
    }
}
