import { onBeforeUnmount } from 'vue'
import { touchpadUrl, type TouchPhase } from 'src/api'

interface Socket {
  ws: WebSocket
  deviceId: string
  // Touches made while the socket is still connecting; sent as soon as it opens.
  pending: string[]
}

// One touchpad socket for the selected device. It opens on the first touch and again after it
// drops; a drag already under way picks up on the new one with a press.
export const useTouchpad = (onError: (message: string) => void) => {
  let socket: Socket | null = null

  const close = () => {
    socket?.ws.close()
    socket = null
  }

  const open = (deviceId: string): Socket => {
    const ws = new WebSocket(touchpadUrl(deviceId))
    const opened: Socket = { ws, deviceId, pending: [] }
    let wasOpen = false
    ws.onopen = () => {
      wasOpen = true
      opened.pending.forEach((message) => ws.send(message))
      opened.pending = []
    }
    // The server only ever sends errors: {"detail": "...", "status": 409}.
    ws.onmessage = (event: MessageEvent<string>) => {
      try {
        const { detail } = JSON.parse(event.data) as { detail?: unknown }
        onError(typeof detail === 'string' ? detail : 'Touchpad error')
      } catch {
        onError('Touchpad error')
      }
    }
    ws.onclose = () => {
      if (socket !== opened) return // closed here, or already replaced
      socket = null
      // A socket that drops after opening closes quietly; only a failure to open is reported.
      if (!wasOpen) onError("Can't reach the Iris server")
    }
    return opened
  }

  // t is when, in ms on the page's clock; the Apple TV takes a swipe's speed from these times.
  const send = (target: Socket, phase: TouchPhase, x: number, y: number, t: number) => {
    const message = JSON.stringify({ phase, x, y, t })
    if (target.ws.readyState === WebSocket.CONNECTING) target.pending.push(message)
    else target.ws.send(message)
  }

  const touch = (deviceId: string, phase: TouchPhase, x: number, y: number, t: number) => {
    const current = socket
    if (current && current.deviceId === deviceId && current.ws.readyState <= WebSocket.OPEN) {
      send(current, phase, x, y, t)
      return
    }
    close()
    if (phase === 'release') return // nothing left to lift
    const fresh = open(deviceId)
    socket = fresh
    if (phase === 'move') send(fresh, 'press', x, y, t)
    send(fresh, phase, x, y, t)
  }

  onBeforeUnmount(close)

  return { touch, close }
}
