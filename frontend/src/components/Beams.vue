<template>
  <div ref="hostRef" :class="['beams-host', className]"></div>
</template>

<script setup>
import { createElement } from 'react';
import { createRoot } from 'react-dom/client';
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';

import ReactBeams from './Beams.jsx';

const props = defineProps({
  beamWidth: { type: Number, default: 2 },
  beamHeight: { type: Number, default: 15 },
  beamNumber: { type: Number, default: 12 },
  lightColor: { type: String, default: '#ffffff' },
  speed: { type: Number, default: 2 },
  noiseIntensity: { type: Number, default: 1.75 },
  scale: { type: Number, default: 0.2 },
  rotation: { type: Number, default: 0 },
  className: { type: String, default: '' }
});

const hostRef = ref(null);
let reactRoot = null;

const renderReact = () => {
  if (!hostRef.value) return;

  if (!reactRoot) {
    reactRoot = createRoot(hostRef.value);
  }

  reactRoot.render(
    createElement(ReactBeams, {
      beamWidth: props.beamWidth,
      beamHeight: props.beamHeight,
      beamNumber: props.beamNumber,
      lightColor: props.lightColor,
      speed: props.speed,
      noiseIntensity: props.noiseIntensity,
      scale: props.scale,
      rotation: props.rotation
    })
  );
};

onMounted(() => {
  renderReact();
});

onBeforeUnmount(() => {
  if (reactRoot) {
    reactRoot.unmount();
    reactRoot = null;
  }
});

watch(
  () => [
    props.beamWidth,
    props.beamHeight,
    props.beamNumber,
    props.lightColor,
    props.speed,
    props.noiseIntensity,
    props.scale,
    props.rotation
  ],
  () => {
    renderReact();
  }
);
</script>

<style scoped>
.beams-host {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
</style>
