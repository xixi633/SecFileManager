<template>
  <div ref="containerRef" :class="['silk-container', className]"></div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue';
import { Renderer, Program, Triangle, Mesh } from 'ogl';

const props = defineProps({
  speed: { type: Number, default: 5 },
  scale: { type: Number, default: 1 },
  color: { type: String, default: '#7B7481' },
  noiseIntensity: { type: Number, default: 1.5 },
  rotation: { type: Number, default: 0 },
  className: { type: String, default: '' }
});

const containerRef = ref(null);
const rendererRef = ref(null);
const uniformsRef = ref(null);
const meshRef = ref(null);
const animationIdRef = ref(null);
const cleanupRef = ref(null);

const hexToNormalizedRGB = hex => {
  if (!hex || typeof hex !== 'string') {
    return [0.482, 0.455, 0.506];
  }

  let value = hex.trim().replace('#', '');
  if (value.length === 3) {
    value = value
      .split('')
      .map(c => c + c)
      .join('');
  }

  if (!/^[0-9a-fA-F]{6}$/.test(value)) {
    return [0.482, 0.455, 0.506];
  }

  return [
    parseInt(value.slice(0, 2), 16) / 255,
    parseInt(value.slice(2, 4), 16) / 255,
    parseInt(value.slice(4, 6), 16) / 255
  ];
};

const vertexShader = `
attribute vec2 position;
attribute vec2 uv;
varying vec2 vUv;

void main() {
  vUv = uv;
  gl_Position = vec4(position, 0.0, 1.0);
}
`;

const fragmentShader = `
precision highp float;

varying vec2 vUv;

uniform float uTime;
uniform vec3  uColor;
uniform float uSpeed;
uniform float uScale;
uniform float uRotation;
uniform float uNoiseIntensity;

const float E = 2.71828182845904523536;

float noise(vec2 texCoord) {
  vec2 r = E * sin(E * texCoord);
  return fract(r.x * r.y * (1.0 + texCoord.x));
}

vec2 rotateUvs(vec2 uv, float angle) {
  float c = cos(angle);
  float s = sin(angle);
  mat2 rot = mat2(c, -s, s, c);
  return rot * uv;
}

void main() {
  float rnd = noise(gl_FragCoord.xy);
  vec2 uv = rotateUvs(vUv * uScale, uRotation);
  vec2 tex = uv * uScale;
  float tOffset = uSpeed * uTime;

  tex.y += 0.03 * sin(8.0 * tex.x - tOffset);

  float pattern = 0.6 +
                  0.4 * sin(5.0 * (tex.x + tex.y +
                                   cos(3.0 * tex.x + 5.0 * tex.y) +
                                   0.02 * tOffset) +
                           sin(20.0 * (tex.x + tex.y - 0.1 * tOffset)));

  vec4 col = vec4(uColor, 1.0) * vec4(pattern) - rnd / 15.0 * uNoiseIntensity;
  col.a = 1.0;
  gl_FragColor = col;
}
`;

const initSilk = () => {
  if (!containerRef.value) return;

  const renderer = new Renderer({
    dpr: Math.min(window.devicePixelRatio, 2),
    alpha: true
  });
  rendererRef.value = renderer;

  const gl = renderer.gl;
  gl.canvas.style.width = '100%';
  gl.canvas.style.height = '100%';
  gl.canvas.style.display = 'block';

  while (containerRef.value.firstChild) {
    containerRef.value.removeChild(containerRef.value.firstChild);
  }
  containerRef.value.appendChild(gl.canvas);

  const uniforms = {
    uTime: { value: 0 },
    uColor: { value: hexToNormalizedRGB(props.color) },
    uSpeed: { value: props.speed },
    uScale: { value: props.scale },
    uRotation: { value: props.rotation },
    uNoiseIntensity: { value: props.noiseIntensity }
  };
  uniformsRef.value = uniforms;

  const geometry = new Triangle(gl);
  const program = new Program(gl, {
    vertex: vertexShader,
    fragment: fragmentShader,
    uniforms
  });
  meshRef.value = new Mesh(gl, { geometry, program });

  const resize = () => {
    if (!containerRef.value || !rendererRef.value) return;
    const { clientWidth, clientHeight } = containerRef.value;
    rendererRef.value.setSize(clientWidth, clientHeight);
  };

  let lastTime = 0;
  const loop = time => {
    if (!rendererRef.value || !uniformsRef.value || !meshRef.value) return;

    if (lastTime === 0) {
      lastTime = time;
    }

    const delta = (time - lastTime) / 1000;
    lastTime = time;

    uniformsRef.value.uTime.value += 0.1 * delta;
    rendererRef.value.render({ scene: meshRef.value });

    animationIdRef.value = requestAnimationFrame(loop);
  };

  window.addEventListener('resize', resize);
  resize();
  animationIdRef.value = requestAnimationFrame(loop);

  cleanupRef.value = () => {
    if (animationIdRef.value) {
      cancelAnimationFrame(animationIdRef.value);
      animationIdRef.value = null;
    }

    window.removeEventListener('resize', resize);

    try {
      const canvas = renderer.gl.canvas;
      const loseContextExt = renderer.gl.getExtension('WEBGL_lose_context');
      if (loseContextExt) {
        loseContextExt.loseContext();
      }

      if (canvas && canvas.parentNode) {
        canvas.parentNode.removeChild(canvas);
      }
    } catch (error) {
      console.warn('Silk cleanup error:', error);
    }

    meshRef.value = null;
    uniformsRef.value = null;
    rendererRef.value = null;
  };
};

onMounted(() => {
  initSilk();
});

onBeforeUnmount(() => {
  if (cleanupRef.value) {
    cleanupRef.value();
  }
});

watch(
  () => [props.speed, props.scale, props.color, props.noiseIntensity, props.rotation],
  () => {
    if (!uniformsRef.value) return;

    uniformsRef.value.uSpeed.value = props.speed;
    uniformsRef.value.uScale.value = props.scale;
    uniformsRef.value.uColor.value = hexToNormalizedRGB(props.color);
    uniformsRef.value.uNoiseIntensity.value = props.noiseIntensity;
    uniformsRef.value.uRotation.value = props.rotation;
  }
);
</script>

<style scoped>
.silk-container {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  background: transparent;
}
</style>
