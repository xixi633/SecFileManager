<template>
  <div ref="divRef" @mousemove="handleMouseMove" :class="['card-spotlight', className]">
    <slot></slot>
  </div>
</template>

<script setup>
import { ref } from 'vue';

const props = defineProps({
  className: {
    type: String,
    default: ''
  },
  spotlightColor: {
    type: String,
    default: 'rgba(255, 255, 255, 0.25)'
  }
});

const divRef = ref(null);

const handleMouseMove = (e) => {
  if (!divRef.value) return;
  const rect = divRef.value.getBoundingClientRect();
  const x = e.clientX - rect.left;
  const y = e.clientY - rect.top;

  divRef.value.style.setProperty('--mouse-x', `${x}px`);
  divRef.value.style.setProperty('--mouse-y', `${y}px`);
  divRef.value.style.setProperty('--spotlight-color', props.spotlightColor);
};
</script>

<style scoped>
.card-spotlight {
  position: relative;
  border-radius: 1.5rem;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background-color: rgba(30, 41, 59, 0.4);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5);
  padding: 2rem;
  overflow: hidden;
  --mouse-x: 50%;
  --mouse-y: 50%;
  --spotlight-color: rgba(255, 255, 255, 0.05);
}

.card-spotlight::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: radial-gradient(circle at var(--mouse-x) var(--mouse-y), var(--spotlight-color), transparent 80%);
  opacity: 0;
  transition: opacity 0.5s ease;
  pointer-events: none;
  z-index: 0;
}

.card-spotlight:hover::before,
.card-spotlight:focus-within::before {
  opacity: 0.6;
}

.card-spotlight > :deep(*) {
  position: relative;
  z-index: 1;
}
</style>
