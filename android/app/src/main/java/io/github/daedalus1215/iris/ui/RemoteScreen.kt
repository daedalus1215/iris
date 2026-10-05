package io.github.daedalus1215.iris.ui

import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
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
import io.github.daedalus1215.iris.core.TouchPhase
import io.github.daedalus1215.iris.ui.theme.IrisColors
import kotlin.math.roundToInt

private val KeyShape = RoundedCornerShape(22.dp)
private val KeySpacing = 12.dp
private val PadShape = RoundedCornerShape(28.dp)

/** The Apple TV's touchpad runs 0 to 1000 on each axis. */
private const val TOUCH_RANGE = 1000

/** Moves go out at most this often: about 60 a second, like pyatv's own swipes. */
private const val TOUCH_INTERVAL_MS = 16L

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
            IconKey(R.drawable.ic_power_settings_new, "Turn off", IrisColors.PowerOff, enabled, size = 52.dp) {
                viewModel.send("turn_off")
            }
            IconKey(R.drawable.ic_power, "Turn on", IrisColors.PowerOn, enabled, size = 52.dp) {
                viewModel.send("turn_on")
            }
        }

        val middle = Modifier.weight(1f).fillMaxWidth()
        if (viewModel.arrowButtons) {
            Box(middle, contentAlignment = Alignment.Center) {
                DPad(enabled, onHold = hold, onRelease = release, onSelect = { viewModel.send("select") })
            }
        } else {
            TouchPad(
                enabled = enabled,
                onTouch = viewModel::touch,
                onTap = { viewModel.send("select") },
                onLongPress = { viewModel.send("select", action = "hold") },
                modifier = middle,
            )
        }

        KeyRow {
            IconKey(R.drawable.ic_arrow_back, "Back", IrisColors.Navigation, enabled) {
                viewModel.send("menu")
            }
            IconKey(R.drawable.ic_tv, "Home", IrisColors.Navigation, enabled) {
                viewModel.send("home")
            }
            IconKey(R.drawable.ic_play_pause, "Play or pause", IrisColors.Media, enabled) {
                viewModel.send("play_pause")
            }
        }
        KeyRow {
            IconKey(R.drawable.ic_skip_previous, "Previous", IrisColors.Media, enabled) {
                viewModel.send("previous")
            }
            HoldKey(
                R.drawable.ic_volume_down, "Volume down", enabled, { hold("volume_down") }, release,
                color = IrisColors.Media, size = 64.dp,
            )
            HoldKey(
                R.drawable.ic_volume_up, "Volume up", enabled, { hold("volume_up") }, release,
                color = IrisColors.Media, size = 64.dp,
            )
            IconKey(R.drawable.ic_skip_next, "Next", IrisColors.Media, enabled) {
                viewModel.send("next")
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            serverUrl = viewModel.serverUrl,
            token = viewModel.token,
            arrowButtons = viewModel.arrowButtons,
            onSave = { url, token, arrowButtons ->
                viewModel.saveSettings(url, token, arrowButtons)
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
 * It reads raw touches rather than clicks, so it acts on touch down, with no tap delay.
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

/** The arrow buttons, in place of the touchpad: arrows repeat while held; OK is a single press. */
@Composable
private fun DPad(
    enabled: Boolean,
    onHold: (command: String) -> Unit,
    onRelease: () -> Unit,
    onSelect: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KeySpacing),
    ) {
        HoldKey(R.drawable.ic_keyboard_arrow_up, "Up", enabled, { onHold("up") }, onRelease)
        KeyRow {
            HoldKey(R.drawable.ic_keyboard_arrow_left, "Left", enabled, { onHold("left") }, onRelease)
            TapKey("Select", IrisColors.Navigation, enabled, onClick = onSelect) {
                Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
            }
            HoldKey(R.drawable.ic_keyboard_arrow_right, "Right", enabled, { onHold("right") }, onRelease)
        }
        HoldKey(R.drawable.ic_keyboard_arrow_down, "Down", enabled, { onHold("down") }, onRelease)
    }
}

/**
 * Like the Siri Remote's touch surface. A drag streams the finger to the Apple TV, which moves
 * focus with its own glide (or scrubs, during playback); a tap selects; a long press holds
 * select, for context menus. The pad stands for the remote's whole surface, so where a drag
 * starts matters, as it does on the remote. Taps and long presses send no touches at all, so a
 * tap near an edge can't read as an arrow.
 */
@Composable
private fun TouchPad(
    enabled: Boolean,
    onTouch: (phase: TouchPhase, x: Int, y: Int) -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val currentOnTouch by rememberUpdatedState(onTouch)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    // Where the finger is, for the glow under it. Only drawing reads it, so moves don't recompose.
    var finger by remember { mutableStateOf<Offset?>(null) }
    val touching by remember { derivedStateOf { finger != null } }

    Box(
        modifier = modifier
            // Swipes that start near the screen's edge are for the TV, not the back gesture.
            .systemGestureExclusion()
            .clip(PadShape)
            .background(Brush.verticalGradient(listOf(IrisColors.Surface, IrisColors.BackgroundEnd)))
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.12f else 0.05f), PadShape)
            .drawBehind {
                val at = finger ?: return@drawBehind
                val radius = 64.dp.toPx()
                drawCircle(
                    Brush.radialGradient(
                        listOf(IrisColors.Navigation.copy(alpha = 0.6f), Color.Transparent),
                        center = at,
                        radius = radius,
                    ),
                    radius = radius,
                    center = at,
                )
            }
            .semantics {
                contentDescription = "Touchpad"
                if (enabled) {
                    onClick(label = "Select") {
                        currentOnTap()
                        true
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var at = down.position
                    finger = at
                    fun send(phase: TouchPhase, position: Offset) = currentOnTouch(
                        phase,
                        (position.x / size.width * TOUCH_RANGE).roundToInt().coerceIn(0, TOUCH_RANGE),
                        (position.y / size.height * TOUCH_RANGE).roundToInt().coerceIn(0, TOUCH_RANGE),
                    )
                    try {
                        val start = awaitGestureStart(down.id, down.position) { position ->
                            at = position
                            finger = position
                        }
                        when (start) {
                            GestureStart.TAP -> {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                currentOnTap()
                            }
                            GestureStart.LONG_PRESS -> {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                currentOnLongPress()
                            }
                            GestureStart.DRAG -> streamDrag(down.id, at) { phase, position ->
                                finger = position
                                send(phase, position)
                            }
                            GestureStart.CANCEL -> Unit
                        }
                    } finally {
                        finger = null
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val hintAlpha = when {
            !enabled -> 0.2f
            touching -> 0.15f
            else -> 0.5f
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Swipe to move",
                color = Color.White.copy(alpha = hintAlpha),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Tap to select · Hold for options",
                color = Color.White.copy(alpha = hintAlpha * 0.8f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private enum class GestureStart { TAP, DRAG, LONG_PRESS, CANCEL }

/**
 * Decides what a new touch is: the finger lifts (a tap), moves past touch slop (a drag), or
 * stays put (a long press). [onMove] follows the finger meanwhile.
 */
private suspend fun AwaitPointerEventScope.awaitGestureStart(
    pointer: PointerId,
    downAt: Offset,
    onMove: (Offset) -> Unit,
): GestureStart = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
    var start: GestureStart? = null
    while (start == null) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == pointer }
        start = when {
            change == null -> GestureStart.CANCEL
            // A consumed lift means the system took the gesture over, e.g. for back.
            !change.pressed -> if (change.isConsumed) GestureStart.CANCEL else GestureStart.TAP
            else -> {
                onMove(change.position)
                val moved = (change.position - downAt).getDistance() > viewConfiguration.touchSlop
                if (moved) GestureStart.DRAG else null
            }
        }
    }
    start
} ?: GestureStart.LONG_PRESS

/**
 * Streams a drag until the finger lifts: a press where it was recognized, moves at most every
 * [TOUCH_INTERVAL_MS], and a release where it ended, even if the gesture is cut short.
 */
private suspend fun AwaitPointerEventScope.streamDrag(
    pointer: PointerId,
    from: Offset,
    onTouch: (TouchPhase, Offset) -> Unit,
) {
    var at = from
    var lastSent = 0L
    onTouch(TouchPhase.PRESS, at)
    try {
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == pointer } ?: break
            at = change.position
            if (!change.pressed) break
            change.consume()
            if (change.uptimeMillis - lastSent >= TOUCH_INTERVAL_MS) {
                onTouch(TouchPhase.MOVE, at)
                lastSent = change.uptimeMillis
            }
        }
    } finally {
        onTouch(TouchPhase.RELEASE, at)
    }
}
