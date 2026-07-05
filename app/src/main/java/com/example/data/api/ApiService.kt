package com.example.data.api

import com.example.data.model.*
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface ApiService {
    @GET("index.php")
    suspend fun getSettings(
        @Query("action") action: String = "get_settings"
    ): Map<String, String>

    @FormUrlEncoded
    @POST("index.php")
    suspend fun register(
        @Field("action") action: String = "register",
        @Field("name") name: String,
        @Field("username") username: String,
        @Field("email") email: String,
        @Field("phone") phone: String,
        @Field("password") password: String
    ): User

    @FormUrlEncoded
    @POST("index.php")
    suspend fun login(
        @Field("action") action: String = "login",
        @Field("email") email: String,
        @Field("password") password: String
    ): User

    @POST("index.php")
    suspend fun logout(
        @Query("action") action: String = "logout"
    ): GeneralResponse

    @GET("index.php")
    suspend fun checkAuth(
        @Query("action") action: String = "check_auth"
    ): AuthState

    @GET("index.php")
    suspend fun searchUsers(
        @Query("action") action: String = "search_users",
        @Query("q") query: String
    ): List<SimpleUser>

    @GET("index.php")
    suspend fun getRecentChats(
        @Query("action") action: String = "get_recent_chats"
    ): List<RecentChat>

    @FormUrlEncoded
    @POST("index.php")
    suspend fun sendMessage(
        @Field("action") action: String = "send_message",
        @Field("text") text: String,
        @Field("chat_with") chatWith: String
    ): GeneralResponse

    @FormUrlEncoded
    @POST("index.php")
    suspend fun sendFileMessage(
        @Field("action") action: String = "send_file_message",
        @Field("chat_with") chatWith: String,
        @Field("file_type") fileType: String,
        @Field("file_name") fileName: String,
        @Field("file_url") fileUrl: String
    ): GeneralResponse

    @GET("index.php")
    suspend fun getMessages(
        @Query("action") action: String = "get_messages",
        @Query("chat_with") chatWith: String,
        @Query("after") after: Int = 0
    ): List<Message>

    @FormUrlEncoded
    @POST("index.php")
    suspend fun markSeen(
        @Field("action") action: String = "mark_seen",
        @Field("chat_with") chatWith: String
    ): GeneralResponse

    @FormUrlEncoded
    @POST("index.php")
    suspend fun setTyping(
        @Field("action") action: String = "set_typing",
        @Field("chat_with") chatWith: String,
        @Field("typing") typing: Int
    ): GeneralResponse

    @GET("index.php")
    suspend fun getTyping(
        @Query("action") action: String = "get_typing",
        @Query("chat_with") chatWith: String
    ): GeneralResponse

    @GET("index.php")
    suspend fun checkUpdates(
        @Query("action") action: String = "check_updates",
        @Query("last_times") lastTimes: String // JSON-encoded dictionary of contact_id -> last_time
    ): List<UpdateResponse>

    @FormUrlEncoded
    @POST("index.php")
    suspend fun updateProfile(
        @Field("action") action: String = "update_profile",
        @Field("name") name: String,
        @Field("username") username: String,
        @Field("email") email: String,
        @Field("phone") phone: String,
        @Field("password") password: String? = null
    ): GeneralResponse

    @Multipart
    @POST("index.php")
    suspend fun uploadFile(
        @Query("action") action: String = "upload_file",
        @Part file: MultipartBody.Part
    ): GeneralResponse

    @Multipart
    @POST("index.php")
    suspend fun uploadProfilePic(
        @Query("action") action: String = "upload_profile_pic",
        @Part file: MultipartBody.Part
    ): GeneralResponse

    @GET("index.php")
    suspend fun getUser(
        @Query("action") action: String = "get_user",
        @Query("uid") uid: String
    ): SimpleUser

    // WebRTC endpoints
    @FormUrlEncoded
    @POST("index.php")
    suspend fun startCall(
        @Field("action") action: String = "start_call",
        @Field("target_id") targetId: String,
        @Field("type") type: String, // 'audio' or 'video'
        @Field("sdp") sdp: String
    ): StartCallResponse

    @GET("index.php")
    suspend fun checkCalls(
        @Query("action") action: String = "check_calls"
    ): CallDetails?

    @FormUrlEncoded
    @POST("index.php")
    suspend fun acceptCall(
        @Field("action") action: String = "accept_call",
        @Field("call_id") callId: String,
        @Field("sdp") sdp: String
    ): GeneralResponse

    @GET("index.php")
    suspend fun getCallAnswer(
        @Query("action") action: String = "get_call_answer",
        @Query("call_id") callId: String
    ): CallAnswerResponse

    @FormUrlEncoded
    @POST("index.php")
    suspend fun rejectCall(
        @Field("action") action: String = "reject_call",
        @Field("call_id") callId: String
    ): GeneralResponse

    @FormUrlEncoded
    @POST("index.php")
    suspend fun endCall(
        @Field("action") action: String = "end_call",
        @Field("call_id") callId: String
    ): GeneralResponse

    @GET("index.php")
    suspend fun checkCallStatus(
        @Query("action") action: String = "check_call_status",
        @Query("call_id") callId: String
    ): CallStatusResponse

    @FormUrlEncoded
    @POST("index.php")
    suspend fun submitIce(
        @Field("action") action: String = "submit_ice",
        @Field("call_id") callId: String,
        @Field("candidate") candidate: String
    ): GeneralResponse

    @GET("index.php")
    suspend fun getIce(
        @Query("action") action: String = "get_ice",
        @Query("call_id") callId: String,
        @Query("from_id") fromId: String
    ): List<String>
}

object RetrofitClient {
    const val BASE_URL = "https://chat.hostnibo.shop/"

    val cookieJar = InMemoryCookieJar()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ApiService::class.java)
    }
}
