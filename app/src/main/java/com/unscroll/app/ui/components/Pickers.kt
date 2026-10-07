package com.unscroll.app.ui.components

import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.unscroll.app.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A number entry dialog for custom limits: digits only, confirmed only inside [range]. */
@Composable
fun CustomNumberDialog(
    title: String,
    fieldLabel: String,
    initial: Int?,
    range: IntRange,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial?.toString().orEmpty()) }
    val value = text.toIntOrNull()?.takeIf { it in range }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input -> text = input.filter(Char::isDigit).take(MAX_DIGITS) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(fieldLabel) },
                isError = text.isNotEmpty() && value == null,
            )
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onConfirm) }, enabled = value != null) {
                Text(stringResource(R.string.action_set))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** The system time picker, in the user's 12/24 h format. */
fun pickTime(context: Context, minuteOfDay: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(hour * MINUTES_PER_HOUR + minute) },
        minuteOfDay / MINUTES_PER_HOUR,
        minuteOfDay % MINUTES_PER_HOUR,
        DateFormat.is24HourFormat(context),
    ).show()
}

/** "22:00" or "10:00 PM". */
fun formatMinuteOfDay(minuteOfDay: Int): String =
    LocalTime.of(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR)
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

/** "15m", "1h", "1h 30m". */
@Composable
fun shortMinutes(minutes: Int): String {
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        hours == 0 -> stringResource(R.string.short_minutes, rest)
        rest == 0 -> stringResource(R.string.short_hours, hours)
        else -> stringResource(R.string.short_hours_minutes, hours, rest)
    }
}

private const val MINUTES_PER_HOUR = 60
private const val MAX_DIGITS = 5
