<template>
  <div>
    <q-select
      v-if="props.devices.length > 0"
      filled
      label="Select Device"
      :options="props.devices"
      v-model="selectedDevice"
      emit-value
      map-options
      @update:model-value="onDeviceSelect"
      class="custom-select"
      color="primary-gradient"
      borderless
      standout
      style="width: 100%"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import api from '../../api'

interface Props {
  devices: { label: string; value: string }[]
}

const props = defineProps<Props>()
const selectedDevice = ref<string | null>(null)
const isConnected = ref(false)
const protocol = ref<string | null>(null)

// Emit events for parent component
const emit = defineEmits<{
  (e: 'update:connected', value: boolean): void
}>()

// Load saved device from localStorage
const loadSavedDevice = () => {
  const lastSelectedDevice = localStorage.getItem('selectedAppleTvDevice')
  if (lastSelectedDevice) {
    selectedDevice.value = lastSelectedDevice
  }
}

// Save selected device
const saveSelectedDevice = () => {
  if (selectedDevice.value) {
    localStorage.setItem('selectedAppleTvDevice', selectedDevice.value)
  } else {
    localStorage.removeItem('selectedAppleTvDevice')
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
      protocol.value = connectionResponse.data.protocol
      isConnected.value = true
      emit('update:connected', true)

      // Save selected device to localStorage
      saveSelectedDevice()

      console.log('Connected to Apple TV', selectedDevice.value, 'with protocol', protocol.value)
    } catch (error) {
      console.error('Failed to connect:', error)
      isConnected.value = false
      emit('update:connected', false)
    }
  }
}

// Load saved device when component mounts
onMounted(() => {
  loadSavedDevice()
  // If we have a saved device, try to connect to it
  if (selectedDevice.value) {
    onDeviceSelect()
  }
})

// Expose necessary data and methods to parent
defineExpose({
  selectedDevice,
  protocol,
})
</script>

<style scoped>
.custom-select :deep(*) {
  color: white !important;
}
</style>
