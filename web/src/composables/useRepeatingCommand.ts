import { ref } from 'vue'

type CommandFunction = (command: string) => Promise<void>

export const useRepeatingCommand = (sendCommand: CommandFunction) => {
  const holdInterval = ref<number | null>(null)
  const speedupTimeout = ref<number | null>(null)

  const startRepeating = (command: string) => {
    console.log('startRepeating called with:', command)
    // Send first command immediately
    sendCommand(command)

    // Start repeating every 500ms
    holdInterval.value = window.setInterval(() => {
      console.log('Interval sending command:', command)
      sendCommand(command)
    }, 500)

    // Speed up after 5 seconds
    speedupTimeout.value = window.setTimeout(() => {
      if (holdInterval.value) {
        clearInterval(holdInterval.value)
        holdInterval.value = window.setInterval(() => {
          console.log('Fast interval sending command:', command)
          sendCommand(command)
        }, 100)
      }
    }, 5000)
  }

  const stopRepeating = () => {
    console.log('stopRepeating called')
    if (holdInterval.value) {
      clearInterval(holdInterval.value)
      holdInterval.value = null
    }
    if (speedupTimeout.value) {
      clearTimeout(speedupTimeout.value)
      speedupTimeout.value = null
    }
  }

  return {
    getButtonEvents: (command: string) => ({
      mousedown: () => startRepeating(command),
      mouseup: stopRepeating,
      mouseleave: stopRepeating,
      touchstart: (e: Event) => {
        e.preventDefault()
        startRepeating(command)
      },
      touchend: stopRepeating,
    }),
  }
}
