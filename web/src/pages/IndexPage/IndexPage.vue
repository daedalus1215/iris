<script setup lang="ts">
import { ref } from 'vue'
import api from '../../api'
import DeviceSelect from 'src/components/DeviceSelect/DeviceSelect.vue'
import ScanButton from 'src/components/ScanButton/ScanButton.vue'
import GlowButton from 'src/components/GlowButton/GlowButton.vue'
import { useRepeatingCommand } from 'src/composables/useRepeatingCommand'

const isConnected = ref(false)
const deviceSelect = ref()
const devices = ref<{ label: string; value: string }[]>([])

// Handle devices found from scan
const onDevicesFound = (newDevices: { label: string; value: string }[]) => {
  devices.value = newDevices
  // Save to localStorage
  localStorage.setItem('appletvDevices', JSON.stringify(newDevices))
}

// Load saved devices when component mounts
const loadSavedDevices = () => {
  const savedDevices = localStorage.getItem('appletvDevices')
  if (savedDevices) {
    devices.value = JSON.parse(savedDevices)
  }
}

// Load saved devices on mount
loadSavedDevices()

// Function to send commands like 'up', 'down', etc.
const sendCommand = async (command: string) => {
  console.log('sendCommand called with:', command, {
    isConnected: isConnected.value,
    deviceSelect: deviceSelect.value,
  })

  if (!isConnected.value) {
    console.warn('Not connected to any Apple TV.')
    return
  }

  try {
    console.log('Sending API request for command:', command, {
      mac: deviceSelect.value?.selectedDevice,
      protocol: deviceSelect.value?.protocol,
    })

    await api.post(`/apple-tv/${command}`, {
      mac: deviceSelect.value.selectedDevice,
      protocol: deviceSelect.value.protocol,
    })

    console.log('API request successful for command:', command)
  } catch (error) {
    console.error('Failed to send command:', command, error)
  }
}

// Function to send double select command
// const sendDoubleSelect = () => {
//   sendCommand('select')
//   setTimeout(() => sendCommand('select'), 50)
// }

const { getButtonEvents } = useRepeatingCommand(sendCommand)
</script>

<template>
  <q-page class="flex flex-center">
    <div class="column items-center q-pa-sm" style="width: 100%; max-width: 350px">
      <!-- Device Selection Card -->
      <q-card flat bordered class="full-width q-mb-sm q-elevation-2 q-card-glossy">
        <q-card-section class="bg-gradient text-center q-pa-sm">
          <div class="row q-col-gutter-sm justify-center">
            <GlowButton
              color="negative-gradient"
              icon="power_settings_new"
              :disabled="!isConnected"
              @click="sendCommand('power-off')"
            />
            <ScanButton @devices-found="onDevicesFound" />
            <GlowButton
              color="positive-gradient"
              icon="power"
              :disabled="!isConnected"
              @click="sendCommand('power-on')"
            />
          </div>
          <DeviceSelect
            v-model:connected="isConnected"
            :devices="devices"
            ref="deviceSelect"
            class="q-mt-sm"
          />
        </q-card-section>
      </q-card>

      <!-- Main Remote Control Layout (Centered, Compact Remote Style) -->
      <q-card flat bordered class="full-width q-elevation-2 q-card-glossy">
        <q-card-section class="bg-gradient q-pa-sm">
          <div class="column items-center q-gutter-y-sm">
            <!-- Directional Pad (Compact D-pad) -->
            <div class="row justify-center">
              <GlowButton
                color="secondary-gradient"
                icon="arrow_upward"
                :disabled="!isConnected"
                v-on="getButtonEvents('up')"
              />
            </div>
            <div class="row justify-center q-mt-sm">
              <GlowButton
                color="secondary-gradient"
                icon="arrow_back"
                :disabled="!isConnected"
                v-on="getButtonEvents('left')"
              />
              <GlowButton
                color="secondary-gradient"
                label="OK"
                :disabled="!isConnected"
                v-on="getButtonEvents('select')"
              />
              <GlowButton
                color="secondary-gradient"
                icon="arrow_forward"
                :disabled="!isConnected"
                v-on="getButtonEvents('right')"
              />
              <GlowButton
                color="secondary-gradient"
                icon="arrow_downward"
                :disabled="!isConnected"
                v-on="getButtonEvents('down')"
              />
            </div>
            <div class="row justify-center q-mt-sm">
              <GlowButton
                color="secondary-gradient"
                label="M"
                :disabled="!isConnected"
                @click="sendCommand('menu')"
              />
            </div>

            <!-- Media Controls (Compact, Inline) -->
            <div class="row justify-center q-mt-sm">
              <GlowButton
                color="primary-gradient"
                icon="skip_previous"
                :disabled="!isConnected"
                @click="sendCommand('previous')"
              />
              <GlowButton
                color="primary-gradient"
                icon="play_arrow"
                :disabled="!isConnected"
                @click="sendCommand('play')"
              />
              <GlowButton
                color="primary-gradient"
                icon="skip_next"
                :disabled="!isConnected"
                @click="sendCommand('next')"
              />
              <GlowButton
                color="primary-gradient"
                icon="pause"
                :disabled="!isConnected"
                @click="sendCommand('pause')"
              />
            </div>
          </div>
        </q-card-section>
      </q-card>
    </div>
  </q-page>
</template>

<style scoped>
/* Custom Styling for a "Cooler and Sexier" Look, Traditional Remote Style */
.q-card {
  border-radius: 20px;
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
  padding: 20px;
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

.q-page {
  background: linear-gradient(135deg, #0a0a1a, #16213e);
}

/* Ensure text is readable and stylish */
.q-card-section,
.q-btn-label {
  color: white;
  font-family: 'Roboto', sans-serif;
  letter-spacing: 0.5px;
  font-size: 1.1rem;
}

@media (max-width: 480px) {
  .row.q-col-gutter-md.justify-center {
    flex-wrap: wrap;
  }

  .col-6 {
    width: 45%;
    margin-bottom: 8px;
  }

  .col-12 {
    width: 90%;
  }
}
</style>
