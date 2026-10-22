package com.unifetch.app.ui.page.lock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.unifetch.app.util.AppLock

/** PIN field with a button. Only digits; the button enables at the minimum length. */
@Composable
fun PinEntry(
    label: String,
    buttonText: String,
    enabled: Boolean = true,
    autoFocus: Boolean = true,
    onSubmit: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val canSubmit = enabled && pin.length >= AppLock.MIN_PIN_LENGTH
    fun submit() {
        if (!canSubmit) return
        val entered = pin
        pin = ""
        onSubmit(entered)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedTextField(
            value = pin,
            onValueChange = { new -> if (new.length <= 16 && new.all { it.isDigit() }) pin = new },
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
        Button(
            onClick = { submit() },
            enabled = canSubmit,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(buttonText)
        }
    }

    if (autoFocus) {
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    }
}

/**
 * 3x3 pattern grid. Drag across the dots; [onComplete] receives the dot numbers (0–8, row by
 * row) when the finger is lifted.
 */
@Composable
fun PatternInput(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onComplete: (List<Int>) -> Unit,
) {
    val selected = remember { mutableStateListOf<Int>() }
    var pointer by remember { mutableStateOf<Offset?>(null) }
    val currentOnComplete by rememberUpdatedState(onComplete)
    val dotColor = MaterialTheme.colorScheme.outline
    val activeColor = MaterialTheme.colorScheme.primary

    fun centers(width: Float): List<Offset> {
        val cell = width / 3f
        return (0 until 9).map { Offset(cell * (it % 3) + cell / 2f, cell * (it / 3) + cell / 2f) }
    }

    Canvas(
        modifier =
            modifier.size(260.dp).pointerInput(enabled) {
                if (!enabled) return@pointerInput
                fun hit(position: Offset) {
                    val width = size.width.toFloat()
                    val radius = width / 3f * 0.35f
                    centers(width).forEachIndexed { index, center ->
                        if (index !in selected && (center - position).getDistance() < radius) {
                            selected.add(index)
                        }
                    }
                }
                detectDragGestures(
                    onDragStart = { start ->
                        selected.clear()
                        pointer = start
                        hit(start)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        pointer = change.position
                        hit(change.position)
                    },
                    onDragEnd = {
                        pointer = null
                        val dots = selected.toList()
                        selected.clear()
                        if (dots.isNotEmpty()) currentOnComplete(dots)
                    },
                    onDragCancel = {
                        pointer = null
                        selected.clear()
                    },
                )
            }
    ) {
        val points = centers(size.width)
        val stroke = 6.dp.toPx()
        for (i in 1 until selected.size) {
            drawLine(
                color = activeColor,
                start = points[selected[i - 1]],
                end = points[selected[i]],
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        val last = selected.lastOrNull()
        val finger = pointer
        if (last != null && finger != null) {
            drawLine(
                color = activeColor,
                start = points[last],
                end = finger,
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        points.forEachIndexed { index, center ->
            val isSelected = index in selected
            drawCircle(
                color = if (isSelected) activeColor else dotColor,
                radius = if (isSelected) 12.dp.toPx() else 8.dp.toPx(),
                center = center,
            )
        }
    }
}
