package com.unifetch.app.ui.page.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import com.unifetch.app.R
import com.unifetch.app.util.AppLock
import com.unifetch.app.util.BiometricHelper
import com.unifetch.app.util.BiometricHelper.findFragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shows [content], blanked out while UniFetch is locked so nothing shows before the lock screen
 * (a separate screen on top, [com.unifetch.app.LockActivity]) appears. The content stays in place
 * underneath, so unlocking returns to exactly where you were.
 */
@Composable
fun AppLockGate(onBackWhileLocked: () -> Unit, content: @Composable () -> Unit) {
    val locked by AppLock.locked.collectAsState()
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (locked) {
            BackHandler { onBackWhileLocked() }
            Surface(
                modifier =
                    Modifier.fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().systemBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    // Normally the lock screen opens by itself; this is a fallback.
                    TextButton(
                        onClick = { AppLock.openLockScreen(context) },
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text(stringResource(R.string.app_lock_unlock))
                    }
                }
            }
        }
    }
}

/** The unlock screen (PIN, pattern and fingerprint), shown by [com.unifetch.app.LockActivity]. */
@Composable
fun LockScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var message by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val type = remember { AppLock.type() }
    val biometricEnabled = remember {
        AppLock.isBiometricEnabled() && BiometricHelper.isAvailable(context)
    }
    val biometricTitle = stringResource(R.string.app_lock_unlock_title)
    val usePinText =
        stringResource(
            if (type == AppLock.TYPE_PATTERN) R.string.app_lock_use_pattern
            else R.string.app_lock_use_pin
        )
    val wrongText =
        stringResource(
            if (type == AppLock.TYPE_PATTERN) R.string.app_lock_wrong_pattern
            else R.string.app_lock_wrong_pin
        )
    val lockedOutText = stringResource(R.string.app_lock_too_many_attempts)

    fun showBiometric() {
        val activity = context.findFragmentActivity() ?: return
        BiometricHelper.prompt(activity, biometricTitle, usePinText) {
            AppLock.unlockWithBiometric()
        }
    }

    fun tryUnlock(secret: String) {
        if (checking) return
        checking = true
        scope.launch {
            val ok = withContext(Dispatchers.Default) { AppLock.verify(secret) }
            message =
                when {
                    ok -> null
                    System.currentTimeMillis() < AppLock.lockedOutUntil -> lockedOutText
                    else -> wrongText
                }
            checking = false
        }
    }

    BackHandler { onBack() }

    if (biometricEnabled) {
        LaunchedEffect(Unit) { lifecycleOwner.lifecycle.withResumed { showBiometric() } }
    }

    Surface(
        modifier =
            Modifier.fillMaxSize()
                // Catch all touches so nothing underneath can be used while locked.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.app_lock_locked),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Text(
                text = message ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Box(modifier = Modifier.widthIn(max = 360.dp)) {
                if (type == AppLock.TYPE_PATTERN) {
                    PatternInput(enabled = !checking) {
                        tryUnlock(AppLock.patternToSecret(it))
                    }
                } else {
                    PinEntry(
                        label = stringResource(R.string.app_lock_pin),
                        buttonText = stringResource(R.string.app_lock_unlock),
                        enabled = !checking,
                        autoFocus = !biometricEnabled,
                        onSubmit = { tryUnlock(it) },
                    )
                }
            }
            if (biometricEnabled) {
                TextButton(onClick = { showBiometric() }, modifier = Modifier.padding(top = 16.dp)) {
                    Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                    Text(
                        text = stringResource(R.string.app_lock_use_fingerprint),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
