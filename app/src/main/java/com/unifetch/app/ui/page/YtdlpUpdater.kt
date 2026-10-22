package com.unifetch.app.ui.page

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.unifetch.app.App
import com.unifetch.app.R
import com.unifetch.app.util.ToastUtil
import com.unifetch.app.util.UpdateUtil
import com.unifetch.app.util.YT_DLP_MODE_ASK
import com.unifetch.app.util.YtdlpUpdateManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Checks for a yt-dlp update every time the app opens. In "Ask me first" mode it shows a prompt
 * when an update is available (including one found earlier by the background check).
 */
@Composable
fun YtdlpUpdater() {
    var availableVersion by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withTimeoutOrNull(TimeUnit.MINUTES.toMillis(1)) { App.ytdlpReady.await() }
            ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                YtdlpUpdateManager.checkAndUpdate(YtdlpUpdateManager.Trigger.LAUNCH)
            }
        }
        if (YtdlpUpdateManager.mode() == YT_DLP_MODE_ASK) {
            availableVersion = YtdlpUpdateManager.pendingUpdate()
        }
    }

    availableVersion?.let { version ->
        AlertDialog(
            onDismissRequest = { availableVersion = null },
            icon = { Icon(Icons.Outlined.Update, contentDescription = null) },
            title = { Text(stringResource(R.string.ytdlp_update_available)) },
            text = { Text(stringResource(R.string.ytdlp_update_available_msg, version)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        availableVersion = null
                        App.applicationScope.launch {
                            runCatching { UpdateUtil.updateYtDlp() }
                                .onSuccess {
                                    ToastUtil.makeToastSuspend(
                                        App.context.getString(R.string.yt_dlp_up_to_date) +
                                            " (${YtdlpUpdateManager.installedVersionName()})"
                                    )
                                }
                                .onFailure {
                                    ToastUtil.makeToastSuspend(
                                        App.context.getString(R.string.yt_dlp_update_fail)
                                    )
                                }
                        }
                    }
                ) {
                    Text(stringResource(R.string.ytdlp_update_now))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        availableVersion = null
                        YtdlpUpdateManager.dismissPendingUpdate()
                    }
                ) {
                    Text(stringResource(R.string.ytdlp_not_now))
                }
            },
        )
    }
}
