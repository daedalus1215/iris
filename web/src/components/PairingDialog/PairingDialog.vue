<template>
  <q-dialog
    :model-value="modelValue"
    @update:model-value="(open) => emit('update:modelValue', open)"
  >
    <q-card dark class="pairing-card">
      <q-card-section>
        <div class="text-h6">Pair with {{ device.name }}</div>
        <div class="text-caption">
          Show PIN puts a 4-digit PIN on the TV; type it here. Companion is all the remote needs.
        </div>
      </q-card-section>

      <q-card-section
        v-for="protocol in PROTOCOLS"
        :key="protocol"
        class="row items-center no-wrap q-gutter-sm"
      >
        <div class="col">
          <div>{{ LABELS[protocol].name }}</div>
          <div class="text-caption text-grey-5">{{ LABELS[protocol].use }}</div>
        </div>
        <q-icon v-if="device.paired[protocol]" name="check_circle" color="positive" size="sm" />
        <template v-else-if="session && current === protocol">
          <q-input
            v-model="pin"
            dark
            dense
            filled
            autofocus
            mask="####"
            inputmode="numeric"
            placeholder="PIN"
            style="width: 80px"
            @keyup.enter="submitPin"
          />
          <q-btn
            color="primary"
            label="Pair"
            :loading="busy"
            :disable="pin.length !== 4"
            @click="submitPin"
          />
        </template>
        <q-btn
          v-else
          outline
          no-caps
          label="Show PIN"
          :loading="busy && current === protocol"
          :disable="busy"
          @click="begin(protocol)"
        />
      </q-card-section>

      <q-card-section v-if="error" class="text-negative">{{ error }}</q-card-section>

      <q-card-actions align="right">
        <q-btn v-close-popup flat label="Close" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import {
  errorMessage,
  finishPairing,
  startPairing,
  type Device,
  type PairingProtocol,
} from '../../api'

const props = defineProps<{
  modelValue: boolean
  device: Device
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', open: boolean): void
  (e: 'paired'): void
}>()

const PROTOCOLS: PairingProtocol[] = ['companion', 'airplay']
const LABELS: Record<PairingProtocol, { name: string; use: string }> = {
  companion: { name: 'Companion', use: 'Every button on the remote' },
  airplay: { name: 'AirPlay', use: 'Optional: nothing in Iris needs it yet' },
}

const current = ref<PairingProtocol | null>(null)
const session = ref<string | null>(null)
const pin = ref('')
const busy = ref(false)
const error = ref<string | null>(null)

const begin = async (protocol: PairingProtocol) => {
  busy.value = true
  error.value = null
  current.value = protocol
  pin.value = ''
  try {
    session.value = await startPairing(props.device.id, protocol)
  } catch (e) {
    error.value = errorMessage(e)
    current.value = null
  } finally {
    busy.value = false
  }
}

const submitPin = async () => {
  if (!current.value || !session.value || pin.value.length !== 4) return
  busy.value = true
  error.value = null
  try {
    await finishPairing(props.device.id, current.value, session.value, pin.value)
    emit('paired')
  } catch (e) {
    // The server ends the session on failure, so the next try starts over.
    error.value = errorMessage(e)
  } finally {
    busy.value = false
    session.value = null
    current.value = null
    pin.value = ''
  }
}
</script>

<style scoped>
.pairing-card {
  width: 100%;
  max-width: 360px;
  background: linear-gradient(135deg, #1a1a2e, #16213e);
  border-radius: 20px;
}
</style>
