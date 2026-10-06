package io.github.daedalus1215.iris.core

import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SwipeRailTest {
    @Test
    fun `a swipe that sets out sideways stays level as the thumb arcs down`() {
        // Swipe #7 from the 2026-10-05 log: left, then curving down by the end.
        val rail = SwipeRail.start(dx = -24f, dy = 2f, pressX = 616, pressY = 479)

        assertEquals(SwipeRail.Axis.HORIZONTAL, rail.axis)
        assertEquals(507 to 479, rail.follow(507, 481))
        assertEquals(172 to 479, rail.follow(172, 580))
    }

    @Test
    fun `a swipe that sets out up or down stays on its column`() {
        val rail = SwipeRail.start(dx = 4f, dy = 23f, pressX = 500, pressY = 300)

        assertEquals(SwipeRail.Axis.VERTICAL, rail.axis)
        assertEquals(500 to 800, rail.follow(620, 800))
    }

    @Test
    fun `a diagonal swipe is left free`() {
        val rail = SwipeRail.start(dx = 17f, dy = -17f, pressX = 500, pressY = 500)

        assertNull(rail.axis)
        assertEquals(700 to 300, rail.follow(700, 300))
    }

    @Test
    fun `the limit is 30 degrees either side of an axis`() {
        fun axisAt(degrees: Double): SwipeRail.Axis? {
            val radians = Math.toRadians(degrees)
            return SwipeRail.start(
                (10 * cos(radians)).toFloat(),
                (10 * sin(radians)).toFloat(),
                0,
                0,
            ).axis
        }

        assertEquals(SwipeRail.Axis.HORIZONTAL, axisAt(29.0))
        assertNull(axisAt(31.0))
        assertNull(axisAt(59.0))
        assertEquals(SwipeRail.Axis.VERTICAL, axisAt(61.0))
        assertEquals(SwipeRail.Axis.HORIZONTAL, axisAt(180.0 - 29.0))
    }
}
