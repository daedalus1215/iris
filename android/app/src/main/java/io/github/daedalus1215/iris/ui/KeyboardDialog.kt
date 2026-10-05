package io.github.daedalus1215.iris.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Types into the Apple TV's text field with the phone's own keyboard. It opens when a text field
 * on the TV gets focus, starting from what's already there. Each change replaces what's typed on
 * the TV, so its search results follow along as you type.
 */
@Composable
fun KeyboardDialog(
    deviceName: String,
    initialText: String,
    onType: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length))) }
    val focus = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Type on $deviceName") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { new ->
                    // Moving the cursor changes the value too; only text changes go to the TV.
                    val changed = new.text != value.text
                    value = new
                    if (changed) onType(new.text)
                },
                placeholder = { Text("Type here") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDismiss() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )

    // Straight to typing: focusing the field brings up the phone's keyboard.
    LaunchedEffect(Unit) { focus.requestFocus() }
}
