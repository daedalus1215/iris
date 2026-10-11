<template>
  <GlowButton
    color="secondary-gradient"
    icon="settings_input_antenna"
    :disabled="scanning"
    @click="scanForDevices"
  />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { scanDevices, errorMessage, type Device } from '../../api'
import GlowButton from '../GlowButton/GlowButton.vue'

const emit = defineEmits<{
  (e: 'devices-found', devices: Device[]): void
  (e: 'error', message: string): void
}>()

const scanning = ref(false)

const scanForDevices = async () => {
  scanning.value = true
  try {
    emit('devices-found', await scanDevices())
  } catch (error) {
    emit('error', errorMessage(error))
  } finally {
    scanning.value = false
  }
}
</script>
