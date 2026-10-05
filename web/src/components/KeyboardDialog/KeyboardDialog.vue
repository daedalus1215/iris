<!--
  Types into the Apple TV's text field with the phone's own keyboard. It opens when a text field
  on the TV gets focus. Each change replaces what's typed there, so the TV's search results
  follow along as you type.
-->
<template>
  <q-dialog
    :model-value="modelValue"
    position="top"
    @update:model-value="(open) => emit('update:modelValue', open)"
  >
    <q-card dark class="keyboard-card">
      <q-card-section class="q-pb-none">
        <div class="text-subtitle1">Type on {{ deviceName }}</div>
      </q-card-section>
      <q-card-section>
        <q-input
          v-model="text"
          dark
          filled
          autofocus
          clearable
          autocomplete="off"
          autocapitalize="off"
          placeholder="Type here"
          @update:model-value="(value) => emit('type', String(value ?? ''))"
          @keyup.enter="emit('update:modelValue', false)"
        />
      </q-card-section>
      <q-card-actions align="right">
        <q-btn v-close-popup flat label="Done" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{ modelValue: boolean; deviceName: string; initialText: string }>()
const emit = defineEmits<{
  (e: 'update:modelValue', open: boolean): void
  (e: 'type', text: string): void
}>()

const text = ref(props.initialText)
// Each time it opens, start from what's already in the TV's field.
watch(
  () => props.modelValue,
  (open) => {
    if (open) text.value = props.initialText
  },
)
</script>

<style scoped>
.keyboard-card {
  width: 100%;
  max-width: 400px;
  background: linear-gradient(135deg, #1a1a2e, #16213e);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 20px;
}
</style>
