import axios from 'axios'

export interface Device {
  id: string
  name: string
  address: string
  model: string
  os: string
  paired: { companion: boolean; airplay: boolean }
  connected: boolean
}

export type PairingProtocol = keyof Device['paired']

// Same origin as the page: the backend serves this app, and the dev server proxies /api.
const api = axios.create({ baseURL: '/api' })

const devicePath = (id: string) => `/devices/${encodeURIComponent(id)}`

export const getHealth = async () =>
  (await api.get<{ status: string; env: string; version: string }>('/health')).data

export const listDevices = async () => (await api.get<Device[]>('/devices')).data

export const scanDevices = async () => (await api.post<Device[]>('/devices/scan')).data

export const sendCommand = async (id: string, command: string) => {
  await api.post(`${devicePath(id)}/commands/${command}`)
}

export const startPairing = async (id: string, protocol: PairingProtocol) =>
  (await api.post<{ session: string }>(`${devicePath(id)}/pairing/${protocol}`)).data.session

export const finishPairing = async (
  id: string,
  protocol: PairingProtocol,
  session: string,
  pin: string,
) => {
  await api.post(`${devicePath(id)}/pairing/${protocol}/pin`, { session, pin })
}

// Commands work once either protocol is paired.
export const canControl = (device: Device) => device.paired.companion || device.paired.airplay

export const errorMessage = (error: unknown): string => {
  if (axios.isAxiosError(error)) {
    const detail = error.response?.data?.detail
    if (typeof detail === 'string') return detail
    if (!error.response) return "Can't reach the Iris server"
  }
  return error instanceof Error ? error.message : String(error)
}
