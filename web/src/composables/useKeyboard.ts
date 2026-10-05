import { onBeforeUnmount, ref, watch, type Ref } from 'vue'
import { keyboardUrl, type KeyboardState } from 'src/api'

// After a drop, wait 1 s, then twice as long each time, up to 30 s.
const RETRY_MIN_MS = 1000
const RETRY_MAX_MS = 30_000

// Watches the Apple TV's text field while the page is visible, reconnecting when the socket
// drops (e.g. the server lost the Apple TV). `type` replaces what's typed in the field.
export const useKeyboard = (deviceId: Ref<string | null>, onError: (message: string) => void) => {
  const state = ref<KeyboardState | null>(null)
  let socket: WebSocket | null = null
  let retryTimer: number | undefined
  let failures = 0

  const close = () => {
    window.clearTimeout(retryTimer)
    const current = socket
    socket = null
    current?.close()
    state.value = null
  }

  const open = () => {
    close()
    const id = deviceId.value
    if (!id || document.visibilityState !== 'visible') return
    const ws = new WebSocket(keyboardUrl(id))
    socket = ws
    let gotState = false
    ws.onmessage = (event: MessageEvent<string>) => {
      if (socket !== ws) return
      let message: Partial<KeyboardState> & { detail?: unknown }
      try {
        message = JSON.parse(event.data) as typeof message
      } catch {
        return
      }
      // Errors before the first state are about watching itself; the retry covers those.
      if (typeof message.detail === 'string') {
        if (gotState) onError(message.detail)
        return
      }
      gotState = true
      failures = 0
      state.value = { focused: message.focused === true, text: message.text ?? null }
    }
    ws.onclose = () => {
      if (socket !== ws) return // closed here
      socket = null
      state.value = null
      const delay = Math.min(RETRY_MAX_MS, RETRY_MIN_MS * 2 ** failures++)
      retryTimer = window.setTimeout(open, delay)
    }
  }

  const type = (text: string) => {
    if (socket?.readyState !== WebSocket.OPEN) return
    socket.send(JSON.stringify({ text }))
    if (state.value) state.value = { ...state.value, text }
  }

  const onVisibilityChange = () => (document.visibilityState === 'visible' ? open() : close())
  document.addEventListener('visibilitychange', onVisibilityChange)
  watch(deviceId, open, { immediate: true })
  onBeforeUnmount(() => {
    document.removeEventListener('visibilitychange', onVisibilityChange)
    close()
  })

  return { state, type }
}
