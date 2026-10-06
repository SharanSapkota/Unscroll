package com.unscroll.app.ui.block

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.unscroll.app.R
import com.unscroll.app.domain.blocking.UnlockPhrase

/**
 * "Type this phrase to continue". Paste is not blocked, but autocorrect and suggestions are off so
 * the phrase has to be typed deliberately.
 */
@Composable
fun PhraseInput(onUnlocked: () -> Unit, modifier: Modifier = Modifier) {
    val phrase = stringResource(R.string.unlock_phrase)
    var input by rememberSaveable { mutableStateOf("") }
    val matches = UnlockPhrase.matches(input, phrase)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.unlock_phrase_instruction),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = phrase,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            ),
            label = { Text(stringResource(R.string.unlock_phrase_field)) },
        )
        Button(onClick = onUnlocked, enabled = matches) {
            Text(stringResource(R.string.unlock_phrase_confirm))
        }
    }
}

@Composable
fun PhraseDialog(onUnlocked: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.unlock_phrase_title)) },
        text = { PhraseInput(onUnlocked = onUnlocked) },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
