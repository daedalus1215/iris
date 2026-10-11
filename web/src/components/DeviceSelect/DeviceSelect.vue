<template>
  <q-select
    v-if="devices.length > 0"
    filled
    label="Select Device"
    :options="devices"
    option-value="id"
    option-label="name"
    :model-value="modelValue"
    emit-value
    map-options
    class="custom-select"
    color="primary-gradient"
    borderless
    standout
    style="width: 100%"
    @update:model-value="(id: string) => emit('update:modelValue', id)"
  />
</template>

<script setup lang="ts">
import type { Device } from '../../api'

defineProps<{
  devices: Device[]
  modelValue: string | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', id: string): void
}>()
</script>

<style scoped>
.custom-select :deep(*) {
  color: white !important;
}
</style>
