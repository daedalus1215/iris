package io.github.daedalus1215.iris.core

import kotlin.math.abs
import kotlin.math.atan2

/**
 * Keeps a swipe on the axis it set out along. A thumb sweeps in an arc, so on a phone-sized pad a
 * swipe that starts out left drifts down as it goes, and the Apple TV reads the drift as a move
 * down. A swipe that starts within [MAX_ANGLE_DEGREES] of an axis stays exactly on it, at the
 * press's other coordinate; a more diagonal one is left free.
 */
class SwipeRail private constructor(
    private val pressX: Int,
    private val pressY: Int,
    val axis: Axis?,
) {
    enum class Axis { HORIZONTAL, VERTICAL }

    /** Where to send the finger at [x], [y] (touchpad units). */
    fun follow(x: Int, y: Int): Pair<Int, Int> = when (axis) {
        Axis.HORIZONTAL -> x to pressY
        Axis.VERTICAL -> pressX to y
        null -> x to y
    }

    companion object {
        const val MAX_ANGLE_DEGREES = 30.0

        /**
         * The rail for a swipe pressed at [pressX], [pressY] (touchpad units), after the finger
         * moved [dx], [dy] on screen to get there; that first movement sets the angle.
         */
        fun start(dx: Float, dy: Float, pressX: Int, pressY: Int): SwipeRail {
            val angle = Math.toDegrees(atan2(abs(dy).toDouble(), abs(dx).toDouble()))
            val axis = when {
                dx == 0f && dy == 0f -> null
                angle <= MAX_ANGLE_DEGREES -> Axis.HORIZONTAL
                angle >= 90 - MAX_ANGLE_DEGREES -> Axis.VERTICAL
                else -> null
            }
            return SwipeRail(pressX, pressY, axis)
        }
    }
}
