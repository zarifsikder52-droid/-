package com.example.data.api

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object OtpGatewayManager {
    private val auth = FirebaseAuth.getInstance()
    private val client = OkHttpClient()

    fun isRealGatewayEnabled(context: Context): Boolean {
        return true
    }

    fun sendSms(context: Context, phone: String, code: String, onResult: (Boolean, String) -> Unit) {
        // Firebase Phone Auth uses 'verifyPhoneNumber' to send the verification code.
        // The code parameter is ignored here because Firebase generates its own code.
        val options = com.google.firebase.auth.PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, java.util.concurrent.TimeUnit.SECONDS)
            .setActivity(context as android.app.Activity)
            .setCallbacks(object : com.google.firebase.auth.PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                    onResult(true, "Auto-verified")
                }
                override fun onVerificationFailed(e: FirebaseException) {
                    onResult(false, e.localizedMessage ?: "Verification failed")
                }
                override fun onCodeSent(verificationId: String, token: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken) {
                    onResult(true, verificationId)
                }
            })
            .build()
        com.google.firebase.auth.PhoneAuthProvider.verifyPhoneNumber(options)
    }
}
