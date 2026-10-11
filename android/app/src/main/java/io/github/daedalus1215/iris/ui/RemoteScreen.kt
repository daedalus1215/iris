package io.github.daedalus1215.iris.ui

import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.daedalus1215.iris.R
import io.github.daedalus1215.iris.RemoteViewModel
import io.github.daedalus1215.iris.core.RemoteState
import io.github.daedalus1215.iris.ui.theme.IrisColors

private val KeyShape = RoundedCornerShape(22.dp)
private val KeySpacing = 12.dp

@Composable
fun RemoteScreen(
    viewModel: RemoteViewModel,
    localNetworkAllowed: Boolean,
    onAllowLocalNetwork: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // First launch, with no server address yet: go straight to settings.
    var showSettings by rememberSaveable { mutableStateOf(viewModel.serverUrl.isBlank()) }
    val enabled = state.canControl
    val hold = { command: String -> viewModel.press(command) }
    val release = viewModel::release

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(IrisColors.Background, IrisColors.BackgroundEnd)))
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KeySpacing),
    ) {
        Header(
            env = state.env,
            loading = state.loading,
            onScan = viewModel::scan,
            onPair = state.selected?.let { device -> { viewModel.openPairing(device.id) } },
            onSettings = { showSettings = true },
        )
        DevicePicker(state, onSelect = viewModel::select)
        if (localNetworkAllowed) {
            StatusLine(state, onPair = viewModel::openPairing)
        } else {
            LocalNetworkBanner(onAllowLocalNetwork)
        }

        KeyRow {
            IconKey(R.drawable.ic_power_settings_new, "Turn off", IrisColors.PowerOff, enabled) {
                viewModel.send("turn_off")
            }
            IconKey(R.drawable.ic_power, "Turn on", IrisColors.PowerOn, enabled) {
                viewModel.send("turn_on")
            }
        }

        // D-pad: arrows repeat while held; OK is a single press.
        HoldKey(R.drawable.ic_keyboard_arrow_up, "Up", enabled, { hold("up") }, release)
        KeyRow {
            HoldKey(R.drawable.ic_keyboard_arrow_left, "Left", enabled, { hold("left") }, release)
            TapKey("Select", IrisColors.Navigation, enabled, onClick = { viewModel.send("select") }) {
                Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
            }
            HoldKey(R.drawable.ic_keyboard_arrow_right, "Right", enabled, { hold("right") }, release)
        }
        HoldKey(R.drawable.ic_keyboard_arrow_down, "Down", enabled, { hold("down") }, release)

        KeyRow {
            IconKey(R.drawable.ic_arrow_back, "Back", IrisColors.Navigation, enabled) {
                viewModel.send("menu")
            }
            IconKey(R.drawable.ic_tv, "Home", IrisColors.Navigation, enabled) {
                viewModel.send("home")
            }
        }
        KeyRow {
            IconKey(R.drawable.ic_skip_previous, "Previous", IrisColors.Media, enabled) {
                viewModel.send("previous")
            }
            IconKey(R.drawable.ic_play_pause, "Play or pause", IrisColors.Media, enabled) {
                viewModel.send("play_pause")
            }
            IconKey(R.drawable.ic_skip_next, "Next", IrisColors.Media, enabled) {
                viewModel.send("next")
            }
        }
        KeyRow {
            HoldKey(
                R.drawable.ic_volume_down, "Volume down", enabled, { hold("volume_down") }, release,
                color = IrisColors.Media,
            )
            HoldKey(
                R.drawable.ic_volume_up, "Volume up", enabled, { hold("volume_up") }, release,
                color = IrisColors.Media,
            )
        }
    }

    if (showSettings) {
        SettingsDialog(
            serverUrl = viewModel.serverUrl,
            token = viewModel.token,
            onSave = { url, token ->
                viewModel.saveSettings(url, token)
                showSettings = false
            },
            onDismiss = { showSettings = false },
        )
    }

    val pairing = state.pairing
    val pairingDevice = state.pairingDevice
    if (pairing != null && pairingDevice != null) {
        PairingDialog(
            device = pairingDevice,
            pairing = pairing,
            onStart = viewModel::startPairing,
            onSubmitPin = viewModel::submitPin,
            onClose = viewModel::closePairing,
        )
    }
}

@Composable
private fun Header(
    env: String?,
    loading: Boolean,
    onScan: () -> Unit,
    onPair: (() -> Unit)?,
    onSettings: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Iris",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        if (env != null && env != "prod") {
            Spacer(Modifier.width(8.dp))
            Text(
                env.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Black,
                modifier = Modifier
                    .background(IrisColors.Badge, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        IconButton(onClick = onScan) {
            Icon(painterResource(R.drawable.ic_refresh), "Scan for Apple TVs", tint = Color.White)
        }
        IconButton(onClick = { onPair?.invoke() }, enabled = onPair != null) {
            Icon(
                painterResource(R.drawable.ic_link),
                "Pair this Apple TV",
                tint = if (onPair != null) Color.White else Color.White.copy(alpha = 0.4f),
            )
        }
        IconButton(onClick = onSettings) {
            Icon(painterResource(R.drawable.ic_settings), "Settings", tint = Color.White)
        }
    }
}

@Composable
private fun DevicePicker(state: RemoteState, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.selected
    val label = when {
        selected != null -> "${selected.name} · ${selected.model}"
        state.devices.isEmpty() -> if (state.loading) "Looking for Apple TVs…" else "No Apple TVs found"
        else -> "Choose an Apple TV"
    }
    Box {
        TextButton(onClick = { expanded = true }, enabled = state.devices.isNotEmpty()) {
            Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.devices.forEach { device ->
                DropdownMenuItem(
                    text = { Text("${device.name} · ${device.os}") },
                    onClick = {
                        onSelect(device.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusLine(state: RemoteState, onPair: (deviceId: String) -> Unit) {
    val selected = state.selected
    val error = state.error
    when {
        error != null -> Text(
            error,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        selected != null && !selected.canControl -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${selected.name} isn't paired with this server yet.",
                color = IrisColors.Badge,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { onPair(selected.id) }) { Text("Pair") }
        }
    }
}

@Composable
private fun LocalNetworkBanner(onAllow: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Iris needs the Nearby devices permission to reach the server on your Wi-Fi.",
            color = IrisColors.Badge,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onAllow) { Text("Allow") }
    }
}

@Composable
private fun KeyRow(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(KeySpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** The look shared by every key; [interaction] supplies click or hold handling. */
@Composable
private fun KeyFace(
    color: Color,
    enabled: Boolean,
    size: Dp,
    interaction: Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(KeyShape)
            .background(color.copy(alpha = if (enabled) 0.9f else 0.25f))
            .then(interaction),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun KeyIcon(@DrawableRes icon: Int, description: String, enabled: Boolean, size: Dp) {
    Icon(
        painterResource(icon),
        contentDescription = description,
        tint = Color.White.copy(alpha = if (enabled) 1f else 0.5f),
        modifier = Modifier.size(size * 0.5f),
    )
}

/** A key that acts once, on release. */
@Composable
private fun TapKey(
    description: String,
    color: Color,
    enabled: Boolean,
    size: Dp = 72.dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    KeyFace(
        color = color,
        enabled = enabled,
        size = size,
        interaction = Modifier.clickable(
            enabled = enabled,
            onClickLabel = description,
            role = Role.Button,
        ) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        },
        content = content,
    )
}

@Composable
private fun IconKey(
    @DrawableRes icon: Int,
    description: String,
    color: Color,
    enabled: Boolean,
    size: Dp = 64.dp,
    onClick: () -> Unit,
) = TapKey(description, color, enabled, size, onClick) {
    KeyIcon(icon, description, enabled, size)
}

/**
 * A key that acts the moment it's pressed and keeps going until it's let go.
 * It reads raw touches rather than clicks, so there's no tap delay inside the scrolling column.
 */
@Composable
private fun HoldKey(
    @DrawableRes icon: Int,
    description: String,
    enabled: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    color: Color = IrisColors.Navigation,
    size: Dp = 72.dp,
) {
    val view = LocalView.current
    val interactions = remember { MutableInteractionSource() }
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    KeyFace(
        color = color,
        enabled = enabled,
        size = size,
        interaction = Modifier
            .indication(interactions, ripple())
            .semantics { role = Role.Button }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = { offset ->
                        val press = PressInteraction.Press(offset)
                        interactions.emit(press)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        currentOnPress()
                        val released = tryAwaitRelease()
                        currentOnRelease()
                        interactions.emit(
                            if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press),
                        )
                    },
                )
            },
    ) {
        KeyIcon(icon, description, enabled, size)
    }
}
