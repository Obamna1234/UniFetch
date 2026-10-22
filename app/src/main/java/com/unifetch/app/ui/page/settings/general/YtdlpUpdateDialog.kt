package com.unifetch.app.ui.page.settings.general

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.unifetch.app.R
import com.unifetch.app.ui.component.ConfirmButton
import com.unifetch.app.ui.component.DismissButton
import com.unifetch.app.ui.component.SealDialog
import com.unifetch.app.util.PreferenceUtil.getInt
import com.unifetch.app.util.PreferenceUtil.updateInt
import com.unifetch.app.util.ToastUtil
import com.unifetch.app.util.YT_DLP_MASTER
import com.unifetch.app.util.YT_DLP_MODE_ASK
import com.unifetch.app.util.YT_DLP_MODE_AUTO
import com.unifetch.app.util.YT_DLP_MODE_OFF
import com.unifetch.app.util.YT_DLP_NIGHTLY
import com.unifetch.app.util.YT_DLP_STABLE
import com.unifetch.app.util.YT_DLP_UPDATE_CHANNEL
import com.unifetch.app.util.YT_DLP_UPDATE_MODE
import com.unifetch.app.util.YtdlpUpdateManager
import com.unifetch.app.util.YtdlpUpdateWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun DialogSingleChoiceItem(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    label: String,
    labelContainerColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .selectable(selected = selected, enabled = true, onClick = onClick)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        RadioButton(
            modifier = Modifier.clearAndSetSemantics {},
            selected = selected,
            onClick = onClick,
        )

        Text(text = text, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.weight(1f))
        Surface(modifier.padding(end = 12.dp), shape = CircleShape, color = labelContainerColor) {
            Text(
                modifier = Modifier.padding(4.dp),
                text = label,
                color = MaterialTheme.colorScheme.contentColorFor(labelContainerColor),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier =
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun DialogRadioItem(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .selectable(selected = selected, enabled = true, onClick = onClick)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            modifier = Modifier.clearAndSetSemantics {},
            selected = selected,
            onClick = onClick,
        )
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** yt-dlp update settings: channel, automatic update behaviour, last check and rollback. */
@Composable
fun YtdlpUpdateChannelDialog(modifier: Modifier = Modifier, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedChannel = remember { YT_DLP_UPDATE_CHANNEL.getInt() }
    var channel by remember { mutableIntStateOf(savedChannel) }
    var mode by remember { mutableIntStateOf(YT_DLP_UPDATE_MODE.getInt()) }
    var canRollBack by remember { mutableStateOf(YtdlpUpdateManager.canRollBack()) }
    val rollbackVersion = remember { YtdlpUpdateManager.rollbackVersionName() }
    val versionText = remember { YtdlpUpdateManager.describe() }
    var isRollingBack by remember { mutableStateOf(false) }

    SealDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        confirmButton = {
            ConfirmButton {
                if (channel != savedChannel) YtdlpUpdateManager.onChannelChanged()
                YT_DLP_UPDATE_CHANNEL.updateInt(channel)
                YT_DLP_UPDATE_MODE.updateInt(mode)
                YtdlpUpdateWorker.schedule(context.applicationContext)
                onDismissRequest()
            }
        },
        dismissButton = { DismissButton { onDismissRequest() } },
        title = { Text(text = stringResource(id = R.string.update)) },
        icon = { Icon(Icons.Outlined.SyncAlt, null) },
        text = {
            LazyColumn {
                item {
                    Text(
                        text = "yt-dlp $versionText",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item { SectionTitle(stringResource(id = R.string.update_channel)) }
                item {
                    DialogSingleChoiceItem(
                        text = "yt-dlp-nightly-builds",
                        selected = channel == YT_DLP_NIGHTLY,
                        label = "Nightly",
                        labelContainerColor = MaterialTheme.colorScheme.tertiary,
                    ) {
                        channel = YT_DLP_NIGHTLY
                    }
                }
                item {
                    DialogSingleChoiceItem(
                        text = "yt-dlp",
                        selected = channel == YT_DLP_STABLE,
                        label = "Stable",
                    ) {
                        channel = YT_DLP_STABLE
                    }
                }
                item {
                    DialogSingleChoiceItem(
                        text = "yt-dlp-master-builds",
                        selected = channel == YT_DLP_MASTER,
                        label = "Master",
                        labelContainerColor = MaterialTheme.colorScheme.secondary,
                    ) {
                        channel = YT_DLP_MASTER
                    }
                }
                item { SectionTitle(stringResource(id = R.string.ytdlp_update_mode)) }
                item {
                    DialogRadioItem(
                        text = stringResource(R.string.ytdlp_mode_auto),
                        selected = mode == YT_DLP_MODE_AUTO,
                    ) {
                        mode = YT_DLP_MODE_AUTO
                    }
                }
                item {
                    DialogRadioItem(
                        text = stringResource(R.string.ytdlp_mode_ask),
                        selected = mode == YT_DLP_MODE_ASK,
                    ) {
                        mode = YT_DLP_MODE_ASK
                    }
                }
                item {
                    DialogRadioItem(
                        text = stringResource(R.string.ytdlp_mode_off),
                        selected = mode == YT_DLP_MODE_OFF,
                    ) {
                        mode = YT_DLP_MODE_OFF
                    }
                }
                if (canRollBack) {
                    item {
                        Text(
                            text = stringResource(R.string.ytdlp_rollback_desc, rollbackVersion),
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .padding(top = 16.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            enabled = !isRollingBack,
                            onClick = {
                                isRollingBack = true
                                scope.launch {
                                    val ok =
                                        withContext(Dispatchers.IO) {
                                            YtdlpUpdateManager.rollBack()
                                        }
                                    ToastUtil.makeToast(
                                        if (ok) R.string.ytdlp_rollback_done
                                        else R.string.ytdlp_rollback_failed
                                    )
                                    canRollBack = YtdlpUpdateManager.canRollBack()
                                    isRollingBack = false
                                }
                            },
                        ) {
                            Text(stringResource(R.string.ytdlp_rollback))
                        }
                    }
                }
            }
        },
    )
}
