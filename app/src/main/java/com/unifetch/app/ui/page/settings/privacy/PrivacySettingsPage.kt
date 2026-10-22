package com.unifetch.app.ui.page.settings.privacy

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.unifetch.app.R
import com.unifetch.app.ui.component.PreferenceInfo
import com.unifetch.app.ui.component.PreferenceItem
import com.unifetch.app.ui.component.PreferenceSingleChoiceItem
import com.unifetch.app.ui.component.PreferenceSubtitle
import com.unifetch.app.ui.component.PreferenceSwitch
import com.unifetch.app.ui.page.lock.AppLockSetupDialog
import com.unifetch.app.ui.page.settings.BasePreferencePage
import com.unifetch.app.util.APP_LOCK_BIOMETRIC
import com.unifetch.app.util.APP_LOCK_TIMEOUT
import com.unifetch.app.util.AppLock
import com.unifetch.app.util.BiometricHelper
import com.unifetch.app.util.BiometricHelper.findFragmentActivity
import com.unifetch.app.util.NOTIFICATION_DISCREET
import com.unifetch.app.util.PreferenceUtil.getBoolean
import com.unifetch.app.util.PreferenceUtil.updateBoolean
import com.unifetch.app.util.PreferenceUtil.updateLong
import com.unifetch.app.util.QuickWipe
import com.unifetch.app.util.SECURE_SCREEN
import com.unifetch.app.util.ToastUtil
import kotlinx.coroutines.launch

@Composable
fun PrivacySettingsPage(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var lockEnabled by remember { mutableStateOf(AppLock.isEnabled()) }
    var biometric by remember { mutableStateOf(AppLock.isBiometricEnabled()) }
    val biometricAvailable = remember { BiometricHelper.isAvailable(context) }
    var timeout by remember { mutableLongStateOf(AppLock.timeoutMillis()) }
    var secureScreen by remember { mutableStateOf(AppLock.isSecureScreenEnabled()) }
    var discreet by remember { mutableStateOf(NOTIFICATION_DISCREET.getBoolean()) }

    var showSetup by remember { mutableStateOf(false) }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var showWipeDialog by remember { mutableStateOf(false) }
    var wiping by remember { mutableStateOf(false) }

    val fingerprintTitle = stringResource(R.string.app_lock_fingerprint)
    val cancelText = stringResource(R.string.cancel)

    BasePreferencePage(
        modifier = modifier,
        title = stringResource(R.string.privacy_settings),
        onBack = onBack,
    ) {
        LazyColumn(contentPadding = it) {
            item { PreferenceSubtitle(text = stringResource(R.string.app_lock)) }
            item {
                PreferenceSwitch(
                    title = stringResource(R.string.app_lock),
                    description = stringResource(R.string.app_lock_desc),
                    icon = Icons.Outlined.Lock,
                    isChecked = lockEnabled,
                    onClick = {
                        if (lockEnabled) {
                            AppLock.disable()
                            lockEnabled = false
                            biometric = false
                        } else {
                            showSetup = true
                        }
                    },
                )
            }
            item {
                PreferenceItem(
                    title = stringResource(R.string.app_lock_change),
                    description = stringResource(R.string.app_lock_change_desc),
                    icon = Icons.Outlined.Password,
                    enabled = lockEnabled,
                ) {
                    showSetup = true
                }
            }
            item {
                PreferenceSwitch(
                    title = fingerprintTitle,
                    description =
                        stringResource(
                            if (biometricAvailable) R.string.app_lock_fingerprint_desc
                            else R.string.app_lock_fingerprint_unavailable
                        ),
                    icon = Icons.Outlined.Fingerprint,
                    enabled = lockEnabled && biometricAvailable,
                    isChecked = biometric,
                    onClick = {
                        if (biometric) {
                            APP_LOCK_BIOMETRIC.updateBoolean(false)
                            biometric = false
                        } else {
                            // Confirm the fingerprint works before relying on it.
                            context.findFragmentActivity()?.let { activity ->
                                BiometricHelper.prompt(activity, fingerprintTitle, cancelText) {
                                    APP_LOCK_BIOMETRIC.updateBoolean(true)
                                    biometric = true
                                }
                            }
                        }
                    },
                )
            }
            item {
                PreferenceItem(
                    title = stringResource(R.string.app_lock_timeout),
                    description = timeoutText(timeout),
                    icon = Icons.Outlined.Timer,
                    enabled = lockEnabled,
                ) {
                    showTimeoutDialog = true
                }
            }

            item { PreferenceSubtitle(text = stringResource(R.string.privacy_screen)) }
            item {
                PreferenceSwitch(
                    title = stringResource(R.string.secure_screen),
                    description = stringResource(R.string.secure_screen_desc),
                    icon = Icons.Outlined.VisibilityOff,
                    isChecked = secureScreen,
                    onClick = {
                        secureScreen = !secureScreen
                        SECURE_SCREEN.updateBoolean(secureScreen)
                        context.findFragmentActivity()?.let { AppLock.applySecureScreen(it) }
                    },
                )
            }

            item { PreferenceSubtitle(text = stringResource(R.string.privacy_notifications)) }
            item {
                PreferenceSwitch(
                    title = stringResource(R.string.discreet_notifications),
                    description = stringResource(R.string.discreet_notifications_desc),
                    icon = Icons.Outlined.NotificationsOff,
                    isChecked = discreet,
                    onClick = {
                        discreet = !discreet
                        NOTIFICATION_DISCREET.updateBoolean(discreet)
                    },
                )
            }
            item { PreferenceInfo(text = stringResource(R.string.notifications_off_hint)) }

            item { PreferenceSubtitle(text = stringResource(R.string.privacy_data)) }
            item {
                PreferenceItem(
                    title = stringResource(R.string.quick_wipe),
                    description = stringResource(R.string.quick_wipe_desc),
                    icon = Icons.Outlined.DeleteSweep,
                    enabled = !wiping,
                ) {
                    showWipeDialog = true
                }
            }
            item { PreferenceInfo(text = stringResource(R.string.backup_excluded_info)) }
        }
    }

    if (showSetup) {
        AppLockSetupDialog(
            onDismissRequest = { showSetup = false },
            onDone = {
                showSetup = false
                lockEnabled = AppLock.isEnabled()
                ToastUtil.makeToast(R.string.app_lock_saved)
            },
        )
    }

    if (showTimeoutDialog) {
        AlertDialog(
            onDismissRequest = { showTimeoutDialog = false },
            icon = { Icon(Icons.Outlined.Timer, contentDescription = null) },
            title = { Text(stringResource(R.string.app_lock_timeout)) },
            text = {
                LazyColumn {
                    AppLock.TIMEOUT_OPTIONS.forEach { option ->
                        item {
                            PreferenceSingleChoiceItem(
                                text = timeoutText(option),
                                selected = timeout == option,
                            ) {
                                timeout = option
                                APP_LOCK_TIMEOUT.updateLong(option)
                                showTimeoutDialog = false
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimeoutDialog = false }) { Text(cancelText) }
            },
        )
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            icon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
            title = { Text(stringResource(R.string.quick_wipe_confirm_title)) },
            text = { Text(stringResource(R.string.quick_wipe_confirm_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWipeDialog = false
                        wiping = true
                        scope.launch {
                            val result = QuickWipe.wipe(context)
                            ToastUtil.makeToast(
                                if (result == QuickWipe.Result.DONE) R.string.quick_wipe_done
                                else R.string.quick_wipe_busy
                            )
                            wiping = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.quick_wipe_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) { Text(cancelText) }
            },
        )
    }
}

@Composable
private fun timeoutText(millis: Long): String =
    stringResource(
        when (millis) {
            0L -> R.string.app_lock_timeout_immediately
            30_000L -> R.string.app_lock_timeout_30s
            60_000L -> R.string.app_lock_timeout_1m
            300_000L -> R.string.app_lock_timeout_5m
            else -> R.string.app_lock_timeout_15m
        }
    )
