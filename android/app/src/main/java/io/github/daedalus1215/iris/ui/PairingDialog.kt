package io.github.daedalus1215.iris.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.daedalus1215.iris.R
import io.github.daedalus1215.iris.core.Device
import io.github.daedalus1215.iris.core.PairingProtocol
import io.github.daedalus1215.iris.core.PairingState
import io.github.daedalus1215.iris.ui.theme.IrisColors

private val PROTOCOL_TEXT = mapOf(
    PairingProtocol.COMPANION to ("Companion" to "Every button on the remote"),
    PairingProtocol.AIRPLAY to ("AirPlay" to "Optional: nothing in Iris needs it yet"),
)

@Composable
fun PairingDialog(
    device: Device,
    pairing: PairingState,
    onStart: (PairingProtocol) -> Unit,
    onSubmitPin: (String) -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Pair with ${device.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Tap Show PIN and a 4-digit PIN appears on the TV. Type it here. " +
                        "Companion is all the remote needs.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PairingProtocol.entries.forEach { protocol ->
                    ProtocolRow(device, pairing, protocol, onStart, onSubmitPin)
                }
                pairing.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("Done") }
        },
    )
}

@Composable
private fun ProtocolRow(
    device: Device,
    pairing: PairingState,
    protocol: PairingProtocol,
    onStart: (PairingProtocol) -> Unit,
    onSubmitPin: (String) -> Unit,
) {
    val (name, use) = PROTOCOL_TEXT.getValue(protocol)
    val active = pairing.protocol == protocol
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(use, style = MaterialTheme.typography.bodySmall)
            }
            when {
                device.isPaired(protocol) -> Icon(
                    painterResource(R.drawable.ic_check_circle),
                    contentDescription = "Paired",
                    tint = IrisColors.PowerOn,
                )
                active && pairing.busy -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                active && pairing.awaitingPin -> Unit
                else -> OutlinedButton(onClick = { onStart(protocol) }, enabled = !pairing.busy) {
                    Text("Show PIN")
                }
            }
        }
        if (active && pairing.awaitingPin) {
            PinEntry(busy = pairing.busy, session = pairing.session, onSubmit = onSubmitPin)
        }
    }
}

@Composable
private fun PinEntry(busy: Boolean, session: String?, onSubmit: (String) -> Unit) {
    // A fresh field for every new PIN on the TV.
    var pin by rememberSaveable(session) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(session) { focus.requestFocus() }
    val submit: () -> Unit = { if (pin.length == 4 && !busy) onSubmit(pin) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(4) },
            label = { Text("PIN on the TV") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
        Button(onClick = submit, enabled = pin.length == 4 && !busy) { Text("Pair") }
    }
}
