<template>
  <q-page class="flex flex-center">
    <div class="column items-center q-pa-md" style="width: 100%; max-width: 400px">
      <!-- Scan and Device Selection -->
      <q-card flat bordered class="full-width q-mb-md">
        <q-card-section>
          <q-btn
            color="primary"
            label="Scan for Devices"
            @click="scanForDevices"
            class="full-width"
          />
          <q-select
            v-if="devices.length > 0"
            filled
            label="Select Apple TV"
            :options="devices"
            v-model="selectedDevice"
            emit-value
            map-options
            @update:model-value="onDeviceSelect"
            class="q-mt-sm"
          />
        </q-card-section>
      </q-card>

      <!-- Power Controls -->
      <q-card flat bordered class="full-width q-mb-md">
        <q-card-section>
          <div class="row q-col-gutter-sm">
            <div class="col-6">
              <q-btn
                color="positive"
                label="Turn On"
                @click="sendCommand('on')"
                :disabled="!isConnected"
                class="full-width"
              />
            </div>
            <div class="col-6">
              <q-btn
                color="negative"
                label="Turn Off"
                @click="sendCommand('off')"
                :disabled="!isConnected"
                class="full-width"
              />
            </div>
          </div>
        </q-card-section>
      </q-card>

      <!-- Navigation Controls -->
      <q-card flat bordered class="full-width q-mb-md">
        <q-card-section>
          <div class="column items-center q-gutter-y-sm">
            <q-btn
              color="secondary"
              label="Up"
              @click="sendCommand('up')"
              :disabled="!isConnected"
            />
            <div class="row q-gutter-x-sm justify-center">
              <q-btn
                color="secondary"
                label="Left"
                @click="sendCommand('left')"
                :disabled="!isConnected"
              />
              <q-btn
                color="secondary"
                label="Select"
                @click="sendCommand('select')"
                :disabled="!isConnected"
              />
              <q-btn
                color="secondary"
                label="Click"
                @click="sendCommand('click')"
                :disabled="!isConnected"
              />
              <q-btn
                color="secondary"
                label="Right"
                @click="sendCommand('right')"
                :disabled="!isConnected"
              />
            </div>
            <q-btn
              color="secondary"
              label="Down"
              @click="sendCommand('down')"
              :disabled="!isConnected"
            />
            <q-btn
              color="secondary"
              label="Menu"
              @click="sendCommand('menu')"
              :disabled="!isConnected"
            />
          </div>
        </q-card-section>
      </q-card>

      <!-- Media Controls -->
      <q-card flat bordered class="full-width">
        <q-card-section>
          <div class="row q-col-gutter-sm">
            <div class="col-6">
              <q-btn
                color="primary"
                label="Play"
                @click="sendCommand('play')"
                :disabled="!isConnected"
                class="full-width"
              />
            </div>
            <div class="col-6">
              <q-btn
                color="primary"
                label="Pause"
                @click="sendCommand('pause')"
                :disabled="!isConnected"
                class="full-width"
              />
            </div>
          </div>
          <div class="row q-col-gutter-sm q-mt-sm">
            <div class="col-6">
              <q-btn
                color="primary"
                label="Volume Up"
                @click="sendCommand('volume-up')"
                :disabled="!isConnected"
                class="full-width"
              />
            </div>
            <div class="col-6">
              <q-btn
                color="primary"
                label="Volume Down"
                @click="sendCommand('volume-down')"
                :disabled="!isConnected"
                class="full-width"
              />
            </div>
          </div>
        </q-card-section>
      </q-card>
    </div>
  </q-page>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import api from '../../api'

// State for storing device list, selected device, connection status, and protocol
const devices = ref<{ name: string; mac: string }[]>([])
const selectedDevice = ref<string | null>(null)
const isConnected = ref(false)
const protocol = ref<string | null>(null)

// Function to scan for devices
const scanForDevices = async () => {
  try {
    const response = await api.get('/apple-tv/scan')
    devices.value = response.data.map((device: { name: string; mac: string }) => ({
      label: device.name, // Display name of the device
      value: device.mac, // Unique identifier for the device
    }))
  } catch (error) {
    console.error('Failed to scan devices:', error)
  }
}

// Function to handle device selection, connect, and store constraints
const onDeviceSelect = async () => {
  if (selectedDevice.value) {
    try {
      // Connect to the device, you may need to fetch or calculate protocol dynamically
      const connectionResponse = await api.post('/apple-tv/connect', {
        mac: selectedDevice.value,
        protocol: 'protocol_name', // Replace with actual protocol if necessary
      })

      // Assuming the response provides protocol or other constraints
      protocol.value = connectionResponse.data.protocol // Adjust based on your backend
      isConnected.value = true // Mark as connected

      console.log('Connected to Apple TV', selectedDevice.value, 'with protocol', protocol.value)
    } catch (error) {
      console.error('Failed to connect:', error)
      isConnected.value = false
    }
  }
}

// Function to send commands like 'up', 'down', etc.
const sendCommand = async (command: string) => {
  if (!isConnected.value) {
    console.warn('Not connected to any Apple TV.')
    return
  }

  try {
    await api.post(`/apple-tv/${command}`, {
      mac: selectedDevice.value,
      protocol: protocol.value, // Use the protocol after connecting
    })
  } catch (error) {
    console.error('Failed to send command:', error)
  }
}
</script>
