<!--
  Like the Siri Remote's touch surface. A drag streams the finger to the Apple TV, which moves
  focus with its own glide (or scrubs, during playback); a tap selects; a long press holds
  select, for context menus. The pad stands for the remote's whole surface, so where a drag
  starts matters, as it does on the remote. Taps and long presses send no touches at all, so a
  tap near an edge can't read as an arrow.
-->
<template>
  <div
    ref="pad"
    class="touchpad"
    :class="{ disabled, touching: glow !== null }"
    role="button"
    aria-label="Touchpad: swipe to move, tap to select"
    :aria-disabled="disabled"
    @pointerdown="onDown"
    @pointermove="onMove"
    @pointerup="onUp"
    @pointercancel="onCancel"
    @contextmenu.prevent
  >
    <div v-if="glow" class="glow" :style="{ left: `${glow.x}px`, top: `${glow.y}px` }" />
    <div class="hint">
      <div class="hint-title">Swipe to move</div>
      <div class="hint-caption">Tap to select · Hold for options</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import type { TouchPhase } from 'src/api'

const props = defineProps<{ disabled: boolean }>()
const emit = defineEmits<{
  // t is when, in ms on the page's clock (event.timeStamp).
  (e: 'touch', phase: TouchPhase, x: number, y: number, t: number): void
  (e: 'tap'): void
  (e: 'longPress'): void
}>()

// The Apple TV's touchpad runs 0 to 1000 on each axis.
const TOUCH_RANGE = 1000
// Moves go out at most this often: about 60 a second, like pyatv's own swipes.
const TOUCH_INTERVAL_MS = 16
// How far a finger moves before a touch counts as a drag, and how long it rests for a long press.
const SLOP_PX = 8
const LONG_PRESS_MS = 450

const pad = ref<HTMLDivElement | null>(null)
const glow = ref<{ x: number; y: number } | null>(null)

// The touch being followed: undecided until it lifts, moves past the slop, or rests.
interface Point {
  x: number
  y: number
}

let pointerId: number | null = null
let mode: 'pending' | 'drag' | 'held' = 'pending'
let start: Point = { x: 0, y: 0 }
let last: Point = { x: 0, y: 0 }
let lastTime = 0
// Where the drag was pressed, and where it was last sent; see liftOff.
let pressedAt: Point = { x: 0, y: 0 }
let sentAt: Point = { x: 0, y: 0 }
let lastSent = 0
let longPressTimer: number | undefined

const toTv = (point: Point) => {
  const rect = pad.value!.getBoundingClientRect()
  const scale = (offset: number, length: number) =>
    Math.min(TOUCH_RANGE, Math.max(0, Math.round((offset / length) * TOUCH_RANGE)))
  return [scale(point.x - rect.left, rect.width), scale(point.y - rect.top, rect.height)] as const
}

// Keeps a swipe on the axis it set out along. A thumb sweeps in an arc, so a swipe that starts
// out left drifts down as it goes, and the Apple TV reads the drift as a move down. A swipe that
// starts within RAIL_MAX_DEGREES of an axis stays exactly on it; a more diagonal one stays free.
const RAIL_MAX_DEGREES = 30
let rail: { axis: 'horizontal' | 'vertical' | null; pressTv: readonly [number, number] } = {
  axis: null,
  pressTv: [0, 0],
}

// Set at the press, from how the finger moved on screen to get there (touchpad units are
// stretched to the pad's shape, so the angle comes from screen distances).
const startRail = (moved: Point, pressTv: readonly [number, number]) => {
  const angle = (Math.atan2(Math.abs(moved.y), Math.abs(moved.x)) * 180) / Math.PI
  const axis =
    angle <= RAIL_MAX_DEGREES ? 'horizontal' : angle >= 90 - RAIL_MAX_DEGREES ? 'vertical' : null
  rail = { axis, pressTv }
}

// Where on the TV's touchpad to send a point, kept to the swipe's rail.
const onRail = (point: Point) => {
  const [x, y] = toTv(point)
  if (rail.axis === 'horizontal') return [x, rail.pressTv[1]] as const
  if (rail.axis === 'vertical') return [rail.pressTv[0], y] as const
  return [x, y] as const
}

const follow = (event: PointerEvent) => {
  last = { x: event.clientX, y: event.clientY }
  lastTime = event.timeStamp
  const rect = pad.value!.getBoundingClientRect()
  glow.value = { x: event.clientX - rect.left, y: event.clientY - rect.top }
}

const finish = () => {
  window.clearTimeout(longPressTimer)
  pointerId = null
  glow.value = null
}

const onDown = (event: PointerEvent) => {
  if (props.disabled || pointerId !== null || !event.isPrimary) return
  pad.value!.setPointerCapture(event.pointerId)
  pointerId = event.pointerId
  mode = 'pending'
  start = { x: event.clientX, y: event.clientY }
  follow(event)
  longPressTimer = window.setTimeout(() => {
    mode = 'held'
    navigator.vibrate?.(30)
    emit('longPress')
  }, LONG_PRESS_MS)
}

const onMove = (event: PointerEvent) => {
  if (event.pointerId !== pointerId || mode === 'held') return
  follow(event)
  if (mode === 'pending') {
    if (Math.hypot(last.x - start.x, last.y - start.y) <= SLOP_PX) return
    window.clearTimeout(longPressTimer)
    mode = 'drag'
    const pressTv = toTv(last)
    startRail({ x: last.x - start.x, y: last.y - start.y }, pressTv)
    emit('touch', 'press', ...pressTv, lastTime)
    pressedAt = sentAt = last
    lastSent = event.timeStamp
  } else if (event.timeStamp - lastSent >= TOUCH_INTERVAL_MS) {
    emit('touch', 'move', ...onRail(last), lastTime)
    sentAt = last
    lastSent = event.timeStamp
  }
}

// A finger often slides back a little as it lifts, and a release there would read as a flick
// the other way, so a tail that points back against the drag is dropped. A tail that keeps going
// is kept, so a flick keeps its speed.
const liftOff = (lifted: Point): Point => {
  const drag = { x: sentAt.x - pressedAt.x, y: sentAt.y - pressedAt.y }
  const tail = { x: lifted.x - sentAt.x, y: lifted.y - sentAt.y }
  return drag.x * tail.x + drag.y * tail.y < 0 ? sentAt : lifted
}

const onUp = (event: PointerEvent) => {
  if (event.pointerId !== pointerId) return
  if (mode === 'pending') {
    navigator.vibrate?.(10)
    emit('tap')
  } else if (mode === 'drag') {
    const lifted = liftOff({ x: event.clientX, y: event.clientY })
    emit('touch', 'release', ...onRail(lifted), event.timeStamp)
  }
  finish()
}

// The browser took the touch over (e.g. a system gesture): lift the finger, but it's no tap.
const onCancel = (event: PointerEvent) => {
  if (event.pointerId !== pointerId) return
  if (mode === 'drag') emit('touch', 'release', ...onRail(liftOff(last)), lastTime)
  finish()
}

onBeforeUnmount(() => {
  if (pointerId !== null && mode === 'drag') emit('touch', 'release', ...onRail(last), lastTime)
  finish()
})
</script>

<style scoped>
.touchpad {
  position: relative;
  overflow: hidden;
  width: 100%;
  height: min(320px, 45vh);
  border-radius: 28px;
  background: linear-gradient(180deg, #1a1a2e, #16213e);
  border: 1px solid rgba(255, 255, 255, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  /* The pad gets every touch: no page scroll, zoom, text selection or callout. */
  touch-action: none;
  user-select: none;
  -webkit-user-select: none;
  -webkit-touch-callout: none;
}

.touchpad.disabled {
  cursor: default;
  border-color: rgba(255, 255, 255, 0.05);
}

.glow {
  position: absolute;
  width: 128px;
  height: 128px;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  background: radial-gradient(circle, rgba(74, 143, 224, 0.6), transparent 70%);
  pointer-events: none;
}

.hint {
  text-align: center;
  color: rgba(255, 255, 255, 0.5);
  pointer-events: none;
  transition: opacity 0.2s ease;
}

.touchpad.touching .hint {
  opacity: 0.3;
}

.touchpad.disabled .hint {
  opacity: 0.4;
}

.hint-title {
  font-size: 1.1rem;
}

.hint-caption {
  font-size: 0.8rem;
  opacity: 0.8;
}
</style>
