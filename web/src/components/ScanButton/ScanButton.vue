<template>
  <GlowButton color="secondary-gradient" icon="settings_input_antenna" @click="scanForDevices" />
</template>

<script setup lang="ts">
import { defineEmits } from 'vue'
import api from '../../api'
import GlowButton from '../GlowButton/GlowButton.vue'

const emit = defineEmits<{
  (e: 'devices-found', devices: { label: string; value: string }[]): void
}>()

const scanForDevices = async () => {
  try {
    const response = await api.get('/apple-tv/scan')
    const devices = response.data.map((device: { name: string; mac: string }) => ({
      label: device.name,
      value: device.mac,
    }))
    emit('devices-found', devices)
  } catch (error) {
    console.error('Failed to scan devices:', error)
  }
}
</script>
