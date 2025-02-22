<template>
  <q-page class="flex flex-center">
    <div class="column items-center q-pa-md" style="width: 100%; max-width: 350px">
      <!-- Scan and Device Selection with Power Buttons (Top, Centered) -->
      <q-card flat bordered class="full-width q-mb-md q-elevation-2 q-card-glossy">
        <q-card-section class="bg-gradient text-center q-pa-md">
          <div class="row q-col-gutter-sm justify-center">
            <q-btn
              color="negative-gradient"
              icon="power_settings_new"
              @click="sendCommand('power-off')"
              :disabled="!isConnected"
              class="q-btn-glow"
              unelevated
              size="md"
              rounded
            />
            <q-btn
              color="primary-gradient"
              label="Scan"
              @click="scanForDevices"
              class="q-btn-glow"
              unelevated
              size="md"
              rounded
            />
            <q-btn
              color="positive-gradient"
              icon="power"
              @click="sendCommand('power-on')"
              :disabled="!isConnected"
              class="q-btn-glow"
              unelevated
              size="md"
              rounded
            />
          </div>
          <q-select
            v-if="devices.length > 0"
            filled
            label="Select Device"
            :options="devices"
            v-model="selectedDevice"
            emit-value
            map-options
            @update:model-value="onDeviceSelect"
            class="q-mt-sm"
            color="primary-gradient"
            borderless
            standout
            style="width: 100%"
          />
        </q-card-section>
      </q-card>

      <!-- Main Remote Control Layout (Centered, Compact Remote Style) -->
      <q-card flat bordered class="full-width q-elevation-2 q-card-glossy">
        <q-card-section class="bg-gradient q-pa-md">
          <div class="column items-center q-gutter-y-sm">
            <!-- Directional Pad (Compact D-pad) -->
            <div class="row justify-center">
              <q-btn
                color="secondary-gradient"
                icon="arrow_upward"
                @click="sendCommand('up')"
                :disabled="!isConnected"
                class="q-btn-glow"
                unelevated
                size="md"
                rounded
              />
            </div>
            <div class="row justify-center q-mt-sm">
              <q-btn
                color="secondary-gradient"
                icon="arrow_back"
                @click="sendCommand('left')"
                :disabled="!isConnected"
                class="q-btn-glow"
                unelevated
                size="md"
                rounded
              />
              <q-btn
                color="secondary-gradient"
                label="OK"
                @click="sendCommand('select')"
                :disabled="!isConnected"
                class="q-btn-glow"
                unelevated
                size="md"
                rounded
              />
              <q-btn
                color="secondary-gradient"
                label="M"
                @click="sendCommand('menu')"
                :disabled="!isConnected"
                class="q-btn-glow"
                unelevated
                size="md"
                rounded
              />
              <q-btn
                color="secondary-gradient"
                icon="arrow_forward"
                @click="sendCommand('right')"
                :disabled="!isConnected"
                class="q-btn-glow"
                unelevated
                size="md"
                rounded
              />
            </div>
            <div class="row justify-center q-mt-sm">
              <q-btn
                color="secondary-gradient"
                icon="arrow_downward"
                @click="sendCommand('down')"
                :disabled="!isConnected"
                class="q-btn-glow"
                unelevated
                size="md"
                rounded
              />
            </div>

            <!-- Media Controls (Compact, Inline) -->
            <div class="row justify-center q-mt-md">
              <q-btn
                color="primary-gradient"
                icon="skip_previous"
                @click="sendCommand('previous')"
                :disabled="!isConnected"
                class="q-btn-glow"
                unelevated
                size="md"
                rounded
              />
              <q-btn
                color="primary-gradient"
                icon="play_arrow"
                @click="sendCommand('play')"
                :disabled="!isConnected"
                class="q-btn-glow"
                unelevated
                size="md"
                rounded
              />
              <q-btn
                color="primary-gradient"
                icon="pause"
                @click="sendCommand('pause')"
                :disabled="!isConnected"
                class="q-btn-glow"
                unelevated
                size="md"
                rounded
              />
              <q-btn
                color="primary-gradient"
                icon="skip_next"
                @click="sendCommand('next')"
                :disabled="!isConnected"
                class="q-btn-glow"
                unelevated
                size="md"
                rounded
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

<style scoped>
/* Custom Styling for a "Cooler and Sexier" Look, Traditional Remote Style */
.q-card {
  border-radius: 20px; /* Slightly larger for better touch targets */
  transition:
    transform 0.3s ease,
    box-shadow 0.3s ease;
}

.q-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 10px 20px rgba(0, 0, 0, 0.2);
}

.q-card-glossy {
  background: linear-gradient(135deg, #1a1a2e, #16213e);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.bg-gradient {
  background: linear-gradient(135deg, #1a1a2e, #16213e);
  border-radius: 20px;
  padding: 20px; /* Increased padding for touch comfort */
}

.q-btn {
  border-radius: 15px; /* Larger radius for touch */
  text-transform: uppercase;
  font-weight: bold;
  transition: all 0.3s ease;
  min-width: 120px; /* Ensure buttons are wide enough for thumb taps */
  padding: 16px 24px; /* Larger padding for touch targets */
}

.q-btn-glow {
  box-shadow: 0 0 15px rgba(100, 100, 255, 0.5);
}

.q-btn:hover {
  transform: scale(1.05);
  box-shadow: 0 0 20px rgba(100, 100, 255, 0.7);
}

.q-btn.unelevated {
  background: linear-gradient(45deg, #4ecdc4, #45b7d1);
}

.primary-gradient {
  background: linear-gradient(45deg, #ff6b6b, #ff8e53);
  color: white;
}

.secondary-gradient {
  background: linear-gradient(45deg, #5d9cec, #4ecdc4);
  color: white;
}

.positive-gradient {
  background: linear-gradient(45deg, #00ff87, #00d2d2);
  color: white;
}

.negative-gradient {
  background: linear-gradient(45deg, #ff4d4d, #ff6b6b);
  color: white;
}

.grey-gradient {
  background: linear-gradient(45deg, #808080, #606060);
  color: white;
}

.q-select {
  border-radius: 15px;
  font-weight: bold;
  width: 100%; /* Ensure full width for touch */
}

.q-select .q-field__control {
  background: rgba(255, 255, 255, 0.1) !important;
  color: white;
  border-radius: 15px;
  padding: 12px;
}

.q-select .q-field__control:hover {
  background: rgba(255, 255, 255, 0.2) !important;
}

.q-page {
  background: linear-gradient(135deg, #0a0a1a, #16213e);
}

/* Ensure text is readable and stylish */
.q-card-section,
.q-btn-label {
  color: white;
  font-family: 'Roboto', sans-serif;
  letter-spacing: 0.5px;
  font-size: 1.1rem; /* Slightly larger text for readability */
}

/* Responsive adjustments for cellphone screens */
@media (max-width: 480px) {
  .q-card {
    margin: 10px 0;
    width: 100%;
  }

  .q-btn {
    min-width: 100px; /* Adjust button width for smaller screens */
    padding: 14px 20px; /* Slightly smaller padding on very small screens */
  }

  .row {
    flex-direction: column; /* Stack rows vertically on very small screens */
    align-items: center; /* Center-aligned for balance, but still thumb-friendly */
  }

  .col-6 {
    width: 100%; /* Full width for buttons on small screens */
    margin-bottom: 10px; /* Add space between stacked buttons */
  }

  .q-card-section {
    padding: 16px; /* Adjust padding for smaller screens */
  }
}
</style>
