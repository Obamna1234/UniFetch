package com.unifetch.app.ui.page.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.unifetch.app.R
import com.unifetch.app.util.AppLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Choose PIN or pattern, enter it twice, then save. Calls [onDone] once saved. */
@Composable
fun AppLockSetupDialog(onDismissRequest: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var type by remember { mutableIntStateOf(AppLock.TYPE_PIN) }
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val tooShortPin = stringResource(R.string.app_lock_pin_too_short, AppLock.MIN_PIN_LENGTH)
    val tooShortPattern =
        stringResource(R.string.app_lock_pattern_too_short, AppLock.MIN_PATTERN_DOTS)
    val noMatch = stringResource(R.string.app_lock_no_match)

    fun handle(secret: String, length: Int) {
        val minimum =
            if (type == AppLock.TYPE_PATTERN) AppLock.MIN_PATTERN_DOTS else AppLock.MIN_PIN_LENGTH
        val firstEntry = first
        when {
            firstEntry == null && length < minimum -> {
                error = if (type == AppLock.TYPE_PATTERN) tooShortPattern else tooShortPin
            }
            firstEntry == null -> {
                error = null
                first = secret
            }
            secret != firstEntry -> {
                error = noMatch
                first = null
            }
            else -> {
                saving = true
                scope.launch {
                    withContext(Dispatchers.Default) { AppLock.setSecret(type, secret) }
                    onDone()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(24.dp),
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_lock_setup_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (first == null) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        LockTypeOption(
                            text = stringResource(R.string.app_lock_pin),
                            selected = type == AppLock.TYPE_PIN,
                            modifier = Modifier.weight(1f),
                        ) {
                            type = AppLock.TYPE_PIN
                            error = null
                        }
                        LockTypeOption(
                            text = stringResource(R.string.app_lock_pattern),
                            selected = type == AppLock.TYPE_PATTERN,
                            modifier = Modifier.weight(1f),
                        ) {
                            type = AppLock.TYPE_PATTERN
                            error = null
                        }
                    }
                }
                Text(
                    text =
                        stringResource(
                            when {
                                first != null -> R.string.app_lock_confirm
                                type == AppLock.TYPE_PATTERN -> R.string.app_lock_draw_pattern
                                else -> R.string.app_lock_enter_pin
                            }
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                // A fresh input for each step, so the field and pattern start empty.
                key(type, first) {
                    if (type == AppLock.TYPE_PATTERN) {
                        PatternInput(enabled = !saving) {
                            handle(AppLock.patternToSecret(it), it.size)
                        }
                    } else {
                        PinEntry(
                            label = stringResource(R.string.app_lock_pin),
                            buttonText =
                                stringResource(
                                    if (first == null) R.string.app_lock_next
                                    else R.string.app_lock_save
                                ),
                            enabled = !saving,
                            onSubmit = { handle(it, it.length) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.app_lock_forget_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onDismissRequest, enabled = !saving) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun LockTypeOption(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier.selectable(selected = selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}
