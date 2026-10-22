package com.unifetch.app.util

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Fingerprint (strong biometric) prompt for the app lock. */
object BiometricHelper {
    private const val TAG = "BiometricHelper"

    /** True if the phone has a fingerprint (or other strong biometric) set up. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    tailrec fun Context.findFragmentActivity(): FragmentActivity? =
        when (this) {
            is FragmentActivity -> this
            is ContextWrapper -> baseContext.findFragmentActivity()
            else -> null
        }

    fun prompt(
        activity: FragmentActivity,
        title: String,
        negativeButtonText: String,
        onSuccess: () -> Unit,
    ) {
        try {
            val prompt =
                BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(
                            result: BiometricPrompt.AuthenticationResult
                        ) {
                            onSuccess()
                        }
                    },
                )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setNegativeButtonText(negativeButtonText)
                    .setAllowedAuthenticators(BIOMETRIC_STRONG)
                    .build()
            )
        } catch (e: Exception) {
            Log.w(TAG, "couldn't show fingerprint prompt", e)
        }
    }
}
