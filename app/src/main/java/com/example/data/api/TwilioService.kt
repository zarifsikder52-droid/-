package com.example.data.api

import android.util.Base64
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class TwilioService(
    private val accountSid: String,
    private val authToken: String,
    private val messagingSid: String
) {
    private val client = OkHttpClient()

    fun sendSms(to: String, body: String): Result<Boolean> {
        return try {
            val auth = "$accountSid:$authToken"
            val encodedAuth = Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP)
            
            val formBody = FormBody.Builder()
                .add("To", to)
                .add("From", messagingSid)
                .add("Body", body)
                .build()

            val request = Request.Builder()
                .url("https://api.twilio.com/2010-04-01/Accounts/$accountSid/Messages.json")
                .post(formBody)
                .addHeader("Authorization", "Basic $encodedAuth")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected code $response")
                }
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
