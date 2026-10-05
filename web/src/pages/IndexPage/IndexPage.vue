<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { canControl, errorMessage, getHealth, listDevices, sendCommand, type Device } from 'src/api'
import DeviceSelect from 'src/components/DeviceSelect/DeviceSelect.vue'
import ScanButton from 'src/components/ScanButton/ScanButton.vue'
import GlowButton from 'src/components/GlowButton/GlowButton.vue'
import PairingDialog from 'src/components/PairingDialog/PairingDialog.vue'
import TouchPad from 'src/components/TouchPad/TouchPad.vue'
import KeyboardDialog from 'src/components/KeyboardDialog/KeyboardDialog.vue'
import { useRepeatingCommand } from 'src/composables/useRepeatingCommand'
import { useTouchpad } from 'src/composables/useTouchpad'
import { useKeyboard } from 'src/composables/useKeyboard'
import type { TouchPhase } from 'src/api'

const SELECTED_KEY = 'iris.selectedDevice'
const ARROW_BUTTONS_KEY = 'iris.arrowButtons'
// Keys from before the Python backend; the device list now comes from the server.
const LEGACY_DEVICES_KEY = 'appletvDevices'
const LEGACY_SELECTED_KEY = 'selectedAppleTvDevice'

const devices = ref<Device[]>([])
const selectedId = ref<string | null>(
  localStorage.getItem(SELECTED_KEY) ?? localStorage.getItem(LEGACY_SELECTED_KEY),
)
localStorage.removeItem(LEGACY_DEVICES_KEY)
localStorage.removeItem(LEGACY_SELECTED_KEY)

const env = ref<string | null>(null)
const error = ref<string | null>(null)
const pairingOpen = ref(false)

const selected = computed(() => devices.value.find((d) => d.id === selectedId.value) ?? null)
const ready = computed(() => selected.value !== null && canControl(selected.value))
const needsPairing = computed(
  () =>
    selected.value !== null && !(selected.value.paired.companion && selected.value.paired.airplay),
)

// Arrow buttons in place of the touchpad.
const arrowButtons = ref(localStorage.getItem(ARROW_BUTTONS_KEY) === 'true')

watch(selectedId, (id) => {
  if (id) localStorage.setItem(SELECTED_KEY, id)
})
watch(arrowButtons, (arrows) => localStorage.setItem(ARROW_BUTTONS_KEY, String(arrows)))

const onDevicesFound = (found: Device[]) => {
  devices.value = found
  error.value = null
  const [only] = found
  if (!selected.value && only && found.length === 1) selectedId.value = only.id
}

const refreshDevices = async () => {
  try {
    onDevicesFound(await listDevices())
  } catch (e) {
    error.value = errorMessage(e)
  }
}

const send = async (command: string, action?: string) => {
  if (!selected.value) return
  try {
    await sendCommand(selected.value.id, command, action)
    error.value = null
  } catch (e) {
    error.value = errorMessage(e)
  }
}

const { getButtonEvents } = useRepeatingCommand(send)

// Watch the selected Apple TV's text field once it can be controlled.
const keyboardDevice = computed(() => (ready.value && selected.value ? selected.value.id : null))
const { state: keyboardState, type: typeOnTv } = useKeyboard(
  keyboardDevice,
  (message) => (error.value = message),
)
const keyboardFocused = computed(() => keyboardState.value?.focused === true)
// Closing the dialog keeps it closed until the TV's text field loses focus.
const keyboardDismissed = ref(false)
watch(keyboardFocused, (focused) => {
  if (!focused) keyboardDismissed.value = false
})
const keyboardOpen = computed({
  get: () => keyboardFocused.value && !keyboardDismissed.value,
  set: (open: boolean) => (keyboardDismissed.value = !open),
})

const touchpad = useTouchpad((message) => (error.value = message))
watch(selectedId, touchpad.close)

const touch = (phase: TouchPhase, x: number, y: number) => {
  if (!selected.value || !ready.value) return
  // There's no reply to a touch, so a new drag clears the last error; a failure brings it back.
  if (phase === 'press') error.value = null
  touchpad.touch(selected.value.id, phase, x, y)
}

onMounted(async () => {
  getHealth()
    .then((health) => (env.value = health.env))
    .catch(() => {})
  await refreshDevices()
})
</script>

<template>
  <q-page class="flex flex-center">
    <div class="column items-center q-pa-sm" style="width: 100%; max-width: 350px">
      <q-badge v-if="env && env !== 'prod'" color="orange" class="q-mb-sm">
        {{ env.toUpperCase() }}
      </q-badge>

      <!-- Device Selection Card -->
      <q-card flat bordered class="full-width q-mb-sm q-elevation-2 q-card-glossy">
        <q-card-section class="bg-gradient text-center q-pa-sm">
          <div class="row q-col-gutter-sm justify-center">
            <GlowButton
              color="negative-gradient"
              icon="power_settings_new"
              :disabled="!ready"
              @click="send('turn_off')"
            />
            <ScanButton @devices-found="onDevicesFound" @error="(message) => (error = message)" />
            <GlowButton
              color="positive-gradient"
              icon="power"
              :disabled="!ready"
              @click="send('turn_on')"
            />
          </div>
          <DeviceSelect v-model="selectedId" :devices="devices" class="q-mt-sm" />
          <q-btn
            v-if="needsPairing"
            flat
            dense
            no-caps
            icon="link"
            :label="ready ? 'Finish pairing' : 'Pair this Apple TV'"
            class="q-mt-sm"
            @click="pairingOpen = true"
          />
          <q-btn
            v-if="keyboardFocused && keyboardDismissed"
            flat
            dense
            no-caps
            icon="keyboard"
            label="Type on the TV"
            class="q-mt-sm"
            @click="keyboardDismissed = false"
          />
          <div v-if="error" class="text-negative q-mt-sm error-text">{{ error }}</div>
        </q-card-section>
      </q-card>

      <!-- Main Remote Control Layout (Centered, Compact Remote Style) -->
      <q-card flat bordered class="full-width q-elevation-2 q-card-glossy">
        <q-card-section class="bg-gradient q-pa-sm">
          <div class="column items-center q-gutter-y-sm">
            <TouchPad
              v-if="!arrowButtons"
              :disabled="!ready"
              @touch="touch"
              @tap="send('select')"
              @long-press="send('select', 'hold')"
            />
            <!-- Directional Pad -->
            <template v-else>
              <div class="row justify-center">
                <GlowButton
                  color="secondary-gradient"
                  icon="arrow_upward"
                  :disabled="!ready"
                  v-on="getButtonEvents('up')"
                />
              </div>
              <div class="row justify-center">
                <GlowButton
                  color="secondary-gradient"
                  icon="arrow_back"
                  :disabled="!ready"
                  v-on="getButtonEvents('left')"
                />
                <GlowButton
                  color="secondary-gradient"
                  label="OK"
                  :disabled="!ready"
                  @click="send('select')"
                />
                <GlowButton
                  color="secondary-gradient"
                  icon="arrow_forward"
                  :disabled="!ready"
                  v-on="getButtonEvents('right')"
                />
              </div>
              <div class="row justify-center">
                <GlowButton
                  color="secondary-gradient"
                  icon="arrow_downward"
                  :disabled="!ready"
                  v-on="getButtonEvents('down')"
                />
              </div>
            </template>
            <div class="row justify-center q-mt-sm">
              <GlowButton
                color="secondary-gradient"
                icon="undo"
                :disabled="!ready"
                @click="send('menu')"
              />
              <GlowButton
                color="secondary-gradient"
                icon="tv"
                :disabled="!ready"
                @click="send('home')"
              />
            </div>

            <!-- Media Controls -->
            <div class="row justify-center q-mt-sm">
              <GlowButton
                color="primary-gradient"
                icon="skip_previous"
                :disabled="!ready"
                @click="send('previous')"
              />
              <GlowButton
                color="primary-gradient"
                icon="play_arrow"
                icon-right="pause"
                :disabled="!ready"
                @click="send('play_pause')"
              />
              <GlowButton
                color="primary-gradient"
                icon="skip_next"
                :disabled="!ready"
                @click="send('next')"
              />
            </div>
            <div class="row justify-center">
              <GlowButton
                color="primary-gradient"
                icon="volume_down"
                :disabled="!ready"
                v-on="getButtonEvents('volume_down')"
              />
              <GlowButton
                color="primary-gradient"
                icon="volume_up"
                :disabled="!ready"
                v-on="getButtonEvents('volume_up')"
              />
            </div>
          </div>
        </q-card-section>
      </q-card>

      <q-toggle
        v-model="arrowButtons"
        label="Arrow buttons instead of the touchpad"
        color="secondary"
        dark
        dense
        class="q-mt-md text-grey-5"
      />

      <KeyboardDialog
        v-if="selected"
        v-model="keyboardOpen"
        :device-name="selected.name"
        :initial-text="keyboardState?.text ?? ''"
        @type="typeOnTv"
      />

      <PairingDialog
        v-if="selected"
        v-model="pairingOpen"
        :device="selected"
        @paired="refreshDevices"
      />
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

/* Only where there's a real pointer: on a phone, a tap leaves :hover stuck and the card shifts. */
@media (hover: hover) {
  .q-card:hover {
    transform: translateY(-5px);
    box-shadow: 0 10px 20px rgba(0, 0, 0, 0.2);
  }
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

.error-text {
  font-size: 0.9rem;
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
