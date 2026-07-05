package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class User(
    val uid: String,
    val fullname: String,
    val username: String,
    val email: String? = null,
    val phone: String? = null,
    val profilePic: String? = null,
    val profile_pic: String? = null
) {
    val avatarUrl: String
        get() = profilePic ?: profile_pic ?: "https://ui-avatars.com/api/?name=${fullname.replace(" ", "+")}&background=random&color=fff&bold=true"
}

@JsonClass(generateAdapter = true)
data class SimpleUser(
    val uid: String,
    val fullname: String,
    val username: String,
    val profile_pic: String? = null,
    val profilePic: String? = null
) {
    val avatarUrl: String
        get() = profilePic ?: profile_pic ?: "https://ui-avatars.com/api/?name=${fullname.replace(" ", "+")}&background=random&color=fff&bold=true"
}

@JsonClass(generateAdapter = true)
data class RecentChat(
    val uid: String,
    val fullname: String,
    val username: String,
    val profilePic: String? = null,
    val profile_pic: String? = null,
    val unread: Int = 0
) {
    val avatarUrl: String
        get() = profilePic ?: profile_pic ?: "https://ui-avatars.com/api/?name=${fullname.replace(" ", "+")}&background=random&color=fff&bold=true"
}

@JsonClass(generateAdapter = true)
data class Message(
    val id: Int,
    val sender: String,
    val text: String? = null,
    val type: String, // 'text', 'image', 'video', 'file'
    val fileUrl: String? = null,
    val fileName: String? = null,
    val seen: Boolean = false,
    val time: Long,
    val conversationId: String = ""
)

@JsonClass(generateAdapter = true)
data class UpdateResponse(
    val conversationWith: String? = null,
    val id: Int? = null,
    val sender: String? = null,
    val text: String? = null,
    val type: String? = null,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val seen: Boolean? = null,
    val time: Long? = null,

    val _unreadUpdate: Boolean? = null,
    val contactId: String? = null,
    val unread: Int? = null,
    val fullname: String? = null,
    val profilePic: String? = null,

    val _seenUpdate: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class GeneralResponse(
    val ok: Boolean? = null,
    val error: String? = null,
    val id: Int? = null,
    val time: Long? = null,
    val url: String? = null,
    val type: String? = null,
    val name: String? = null
)

@JsonClass(generateAdapter = true)
data class AuthState(
    val authenticated: Boolean,
    val uid: String? = null,
    val fullname: String? = null,
    val username: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val profilePic: String? = null
)

@JsonClass(generateAdapter = true)
data class StartCallResponse(
    val callId: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class CallStatusResponse(
    val status: String
)

@JsonClass(generateAdapter = true)
data class CallDetails(
    val callId: String,
    val callerId: String,
    val type: String, // 'audio' or 'video'
    val sdpOffer: String,
    val callerName: String,
    val callerPic: String? = null
)

@JsonClass(generateAdapter = true)
data class CallAnswerResponse(
    val sdp_offer: String? = null,
    val sdp_answer: String? = null,
    val status: String? = null
)
