type CommandFunction = (command: string) => Promise<void>

// Like a held key: one press, a pause, then steady repeats until release.
const INITIAL_DELAY_MS = 400
const REPEAT_INTERVAL_MS = 150

const sleep = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms))

export const useRepeatingCommand = (sendCommand: CommandFunction) => {
  // Each press gets a new generation; releasing bumps it, which ends that press's loop.
  let generation = 0

  const startRepeating = async (command: string) => {
    const press = ++generation
    // Each send is awaited, so at most one request is in flight and nothing queues up.
    await sendCommand(command)
    await sleep(INITIAL_DELAY_MS)
    while (press === generation) {
      await sendCommand(command)
      await sleep(REPEAT_INTERVAL_MS)
    }
  }

  const stopRepeating = () => {
    generation++
  }

  return {
    getButtonEvents: (command: string) => ({
      mousedown: () => startRepeating(command),
      mouseup: stopRepeating,
      mouseleave: stopRepeating,
      touchstart: (e: Event) => {
        e.preventDefault()
        void startRepeating(command)
      },
      touchend: stopRepeating,
      touchcancel: stopRepeating,
    }),
  }
}
