<template>
  <q-btn
    :color="color"
    :icon="icon"
    :label="label"
    :disabled="disabled"
    class="q-btn-glow"
    unelevated
    size="sm"
    rounded
    @click="$emit('click')"
  />
</template>

<script setup lang="ts">
interface Props {
  color?: string
  icon?: string
  label?: string
  disabled?: boolean
}

defineProps<Props>()
defineEmits<{
  (e: 'click'): void
}>()
</script>

<style scoped>
.q-btn {
  border-radius: 15px;
  text-transform: uppercase;
  font-weight: bold;
  transition: all 0.3s ease;
  height: 75px;
  min-width: 80px;
  padding: 10px 10px;
  margin: 10px;
}

.q-btn-glow {
  box-shadow: 0 0 15px rgba(100, 100, 255, 0.5);
}

.q-btn:hover {
  transform: scale(1.05);
  box-shadow: 0 0 20px rgba(100, 100, 255, 0.7);
}

@media (max-width: 480px) {
  .q-btn {
    min-width: 70px;
    padding: 8px 16px;
  }
}
</style>
