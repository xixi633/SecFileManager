<template>
  <div :class="['glass-surface', className]" :style="containerStyle">
    <div :class="['glass-inner']" :style="innerStyle">
      <svg style="position: absolute; width: 0; height: 0;">
        <defs>
          <filter :id="filterId" x="-10%" y="-10%" width="120%" height="120%">
            <!-- Displacement -->
            <feTurbulence type="fractalNoise" :baseFrequency="displace" numOctaves="1" result="noise" />
            <feDisplacementMap in="SourceGraphic" in2="noise" :scale="distortionScale" xChannelSelector="R" yChannelSelector="G" result="disp" />
          </filter>
        </defs>
      </svg>
      <div class="glass-bg" :style="backgroundStyle"></div>
      <div class="glass-content">
        <slot />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  width: { type: [Number, String], default: '100%' },
  height: { type: [Number, String], default: '100%' },
  borderRadius: { type: [Number, String], default: 0 },
  className: { type: String, default: '' },
  displace: { type: Number, default: 0 },
  distortionScale: { type: Number, default: 0 },
  redOffset: { type: Number, default: 0 },
  greenOffset: { type: Number, default: 0 },
  blueOffset: { type: Number, default: 0 },
  brightness: { type: Number, default: 100 },
  opacity: { type: Number, default: 0.1 },
  mixBlendMode: { type: String, default: 'normal' }
});

const filterId = `glass-filter-${Math.floor(Math.random() * 10000)}`;

const containerStyle = computed(() => {
  return {
    width: typeof props.width === 'number' ? `${props.width}px` : props.width,
    height: typeof props.height === 'number' ? `${props.height}px` : props.height,
  };
});

const innerStyle = computed(() => {
  return {
    borderRadius: `${props.borderRadius}px`,
  };
});

const backgroundStyle = computed(() => {
    let colorShadow = '';
    if (props.redOffset !== 0 || props.greenOffset !== 0 || props.blueOffset !== 0) {
      colorShadow = `
        inset ${props.redOffset}px 0px 10px rgba(255, 0, 0, 0.1),
        inset ${props.greenOffset}px 0px 10px rgba(0, 255, 0, 0.1),
        inset ${props.blueOffset}px 0px 10px rgba(0, 0, 255, 0.1),`;
    }

    return {
      background: `rgba(255, 255, 255, ${props.opacity})`,
      backdropFilter: `blur(20px)`,
      WebkitBackdropFilter: `blur(20px)`,
      mixBlendMode: props.mixBlendMode,
      boxShadow: colorShadow ? `${colorShadow} 0 10px 30px rgba(0, 0, 0, 0.1)` : `0 4px 30px rgba(0,0,0,0.1)`,
      border: '1px solid rgba(255, 255, 255, 0.1)'
    }
});
</script>

<style scoped>
.glass-surface {
  position: relative;
  display: block;
}
.glass-inner {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.glass-bg {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  transition: all 0.3s ease;
}
.glass-content {
  position: relative;
  z-index: 2;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
}
</style>
