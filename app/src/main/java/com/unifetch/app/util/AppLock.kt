package com.unifetch.app.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.WindowManager
import com.unifetch.app.LockActivity
import com.unifetch.app.util.PreferenceUtil.getBoolean
import com.unifetch.app.util.PreferenceUtil.getInt
import com.unifetch.app.util.PreferenceUtil.getLong
import com.unifetch.app.util.PreferenceUtil.getString
import com.unifetch.app.util.PreferenceUtil.updateBoolean
import com.unifetch.app.util.PreferenceUtil.updateInt
import com.unifetch.app.util.PreferenceUtil.updateString
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App lock (PIN or pattern, optionally fingerprint) and the "hide from recents and screenshots"
 * setting.
 *
 * The PIN or pattern is never stored: only a salted PBKDF2 hash, in app-private storage. The app
 * locks on a cold start and again when it comes back after being in the background for longer
 * than the chosen time.
 */
object AppLock {
    const val TYPE_NONE = 0
    const val TYPE_PIN = 1
    const val TYPE_PATTERN = 2

    const val MIN_PIN_LENGTH = 4
    const val MIN_PATTERN_DOTS = 4

    private const val MAX_ATTEMPTS = 5
    private const val LOCKOUT_MS = 30_000L
    private const val ITERATIONS = 60_000
    private const val KEY_LENGTH_BITS = 256

    /** "Lock after leaving the app" choices, in milliseconds. */
    val TIMEOUT_OPTIONS = listOf(0L, 30_000L, 60_000L, 300_000L, 900_000L)

    private val mutableLocked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = mutableLocked.asStateFlow()

    private var startedActivities = 0
    private var backgroundSince = 0L
    private var failedAttempts = 0
    private var lockScreenOpen = false

    /** While set, wrong attempts are ignored until this time. */
    var lockedOutUntil = 0L
        private set

    fun type(): Int = APP_LOCK_TYPE.getInt()

    fun isEnabled(): Boolean = type() != TYPE_NONE && APP_LOCK_HASH.getString().isNotEmpty()

    fun isBiometricEnabled(): Boolean = isEnabled() && APP_LOCK_BIOMETRIC.getBoolean()

    fun timeoutMillis(): Long = APP_LOCK_TIMEOUT.getLong()

    fun init(application: Application) {
        mutableLocked.value = isEnabled()
        application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
    }

    /** Checks a PIN or pattern. Slow on purpose (key stretching): call off the main thread. */
    fun verify(secret: String): Boolean {
        val now = System.currentTimeMillis()
        if (now < lockedOutUntil) return false
        val expected = APP_LOCK_HASH.getString()
        val salt = APP_LOCK_SALT.getString()
        if (expected.isEmpty() || salt.isEmpty()) return false
        val actual = hash(secret, salt, APP_LOCK_ALGORITHM.getString())
        val ok = MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())
        if (ok) {
            failedAttempts = 0
            mutableLocked.value = false
        } else {
            failedAttempts++
            if (failedAttempts >= MAX_ATTEMPTS) {
                failedAttempts = 0
                lockedOutUntil = System.currentTimeMillis() + LOCKOUT_MS
            }
        }
        return ok
    }

    /** Called after a successful fingerprint check. */
    fun unlockWithBiometric() {
        failedAttempts = 0
        mutableLocked.value = false
    }

    /** Saves a new PIN or pattern. Slow on purpose: call off the main thread. */
    fun setSecret(type: Int, secret: String) {
        val saltBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val salt = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
        val algorithm =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) "PBKDF2WithHmacSHA256"
            else "PBKDF2WithHmacSHA1"
        APP_LOCK_SALT.updateString(salt)
        APP_LOCK_ALGORITHM.updateString(algorithm)
        APP_LOCK_HASH.updateString(hash(secret, salt, algorithm))
        APP_LOCK_TYPE.updateInt(type)
        mutableLocked.value = false
    }

    fun disable() {
        APP_LOCK_TYPE.updateInt(TYPE_NONE)
        APP_LOCK_HASH.updateString("")
        APP_LOCK_SALT.updateString("")
        APP_LOCK_ALGORITHM.updateString("")
        APP_LOCK_BIOMETRIC.updateBoolean(false)
        mutableLocked.value = false
    }

    fun patternToSecret(dots: List<Int>): String = dots.joinToString("-")

    private fun hash(secret: String, salt: String, algorithm: String): String {
        val saltBytes = Base64.decode(salt, Base64.NO_WRAP)
        val spec = PBEKeySpec(secret.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH_BITS)
        try {
            val factory =
                SecretKeyFactory.getInstance(algorithm.ifEmpty { "PBKDF2WithHmacSHA1" })
            return Base64.encodeToString(factory.generateSecret(spec).encoded, Base64.NO_WRAP)
        } finally {
            spec.clearPassword()
        }
    }

    // ---- Hide from recents and screenshots ---------------------------------------------------

    fun isSecureScreenEnabled(): Boolean = SECURE_SCREEN.getBoolean()

    /** Applies the "hide from recents and screenshots" setting to an open screen. */
    fun applySecureScreen(activity: Activity) {
        if (isSecureScreenEnabled()) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    /** Opens the lock screen on top of everything (including any open dialog). */
    fun openLockScreen(context: Context) {
        val intent =
            Intent(context, LockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    // ---- Locking when the app comes back from the background ---------------------------------

    private val lifecycleCallbacks =
        object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                applySecureScreen(activity)
                if (activity is LockActivity) lockScreenOpen = true
            }

            override fun onActivityStarted(activity: Activity) {
                if (startedActivities == 0 && backgroundSince > 0L) {
                    val away = System.currentTimeMillis() - backgroundSince
                    if (isEnabled() && (away >= timeoutMillis() || away < 0)) {
                        mutableLocked.value = true
                    }
                    backgroundSince = 0L
                }
                startedActivities++
            }

            override fun onActivityResumed(activity: Activity) {
                applySecureScreen(activity)
                if (locked.value && activity !is LockActivity && !lockScreenOpen) {
                    lockScreenOpen = true
                    openLockScreen(activity)
                }
            }

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                // Rotating the screen briefly stops the activity; that isn't leaving the app.
                if (startedActivities == 0 && !activity.isChangingConfigurations) {
                    backgroundSince = System.currentTimeMillis()
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                if (activity is LockActivity && !activity.isChangingConfigurations) {
                    lockScreenOpen = false
                }
            }
        }
}
