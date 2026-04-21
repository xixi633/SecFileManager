<template>
  <div ref="containerRef" :class="['light-rays-container', className]" style="position: absolute; top: 0; left: 0; width: 100%; height: 100%; z-index: 0;"></div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue';
import { Renderer, Program, Triangle, Mesh } from 'ogl';

const props = defineProps({
  raysOrigin: { type: String, default: 'top-center' },
  raysColor: { type: String, default: '#ffffff' },
  raysSpeed: { type: Number, default: 1 },
  lightSpread: { type: Number, default: 1 },
  rayLength: { type: Number, default: 2 },
  pulsating: { type: Boolean, default: false },
  fadeDistance: { type: Number, default: 1.0 },
  saturation: { type: Number, default: 1.0 },
  followMouse: { type: Boolean, default: true },
  mouseInfluence: { type: Number, default: 0.1 },
  noiseAmount: { type: Number, default: 0.0 },
  distortion: { type: Number, default: 0.0 },
  className: { type: String, default: '' }
});

const DEFAULT_COLOR = '#ffffff';

const hexToRgb = hex => {
  const m = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
  return m ? [parseInt(m[1], 16) / 255, parseInt(m[2], 16) / 255, parseInt(m[3], 16) / 255] : [1, 1, 1];
};

const getAnchorAndDir = (origin, w, h) => {
  const outside = 0.2;
  switch (origin) {
    case 'top-left':
      return { anchor: [0, -outside * h], dir: [0, 1] };
    case 'top-right':
      return { anchor: [w, -outside * h], dir: [0, 1] };
    case 'left':
      return { anchor: [-outside * w, 0.5 * h], dir: [1, 0] };
    case 'right':
      return { anchor: [(1 + outside) * w, 0.5 * h], dir: [-1, 0] };
    case 'bottom-left':
      return { anchor: [0, (1 + outside) * h], dir: [0, -1] };
    case 'bottom-center':
      return { anchor: [0.5 * w, (1 + outside) * h], dir: [0, -1] };
    case 'bottom-right':
      return { anchor: [w, (1 + outside) * h], dir: [0, -1] };
    default: // "top-center"
      return { anchor: [0.5 * w, -outside * h], dir: [0, 1] };
  }
};

const containerRef = ref(null);
const uniformsRef = ref(null);
const rendererRef = ref(null);
const mouseRef = ref({ x: 0.5, y: 0.5 });
const smoothMouseRef = ref({ x: 0.5, y: 0.5 });
const animationIdRef = ref(null);
const meshRef = ref(null);
const cleanupFunctionRef = ref(null);
const isVisible = ref(false);
const observerRef = ref(null);

onMounted(() => {
  if (!containerRef.value) return;

  observerRef.value = new IntersectionObserver(
    entries => {
      const entry = entries[0];
      isVisible.value = entry.isIntersecting;
    },
    { threshold: 0.1 }
  );

  observerRef.value.observe(containerRef.value);
});

onBeforeUnmount(() => {
  if (observerRef.value) {
    observerRef.value.disconnect();
    observerRef.value = null;
  }
  if (cleanupFunctionRef.value) {
    cleanupFunctionRef.value();
    cleanupFunctionRef.value = null;
  }
});

watch(
  () => [
    isVisible.value,
    props.raysOrigin,
    props.raysColor,
    props.raysSpeed,
    props.lightSpread,
    props.rayLength,
    props.pulsating,
    props.fadeDistance,
    props.saturation,
    props.followMouse,
    props.mouseInfluence,
    props.noiseAmount,
    props.distortion
  ],
  () => {
    if (!isVisible.value || !containerRef.value) return;

    if (cleanupFunctionRef.value) {
      cleanupFunctionRef.value();
      cleanupFunctionRef.value = null;
    }

    const initializeWebGL = async () => {
      if (!containerRef.value) return;

      await new Promise(resolve => setTimeout(resolve, 10));

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

      const vert = `
attribute vec2 position;
varying vec2 vUv;
void main() {
  vUv = position * 0.5 + 0.5;
  gl_Position = vec4(position, 0.0, 1.0);
}`;

      const frag = `precision highp float;

uniform float iTime;
uniform vec2  iResolution;

uniform vec2  rayPos;
uniform vec2  rayDir;
uniform vec3  raysColor;
uniform float raysSpeed;
uniform float lightSpread;
uniform float rayLength;
uniform float pulsating;
uniform float fadeDistance;
uniform float saturation;
uniform vec2  mousePos;
uniform float mouseInfluence;
uniform float noiseAmount;
uniform float distortion;

varying vec2 vUv;

float noise(vec2 st) {
  return fract(sin(dot(st.xy, vec2(12.9898,78.233))) * 43758.5453123);
}

float rayStrength(vec2 raySource, vec2 rayRefDirection, vec2 coord,
                  float seedA, float seedB, float speed) {
  vec2 sourceToCoord = coord - raySource;
  vec2 dirNorm = normalize(sourceToCoord);
  float cosAngle = dot(dirNorm, rayRefDirection);

  float distortedAngle = cosAngle + distortion * sin(iTime * 2.0 + length(sourceToCoord) * 0.01) * 0.2;
  
  float spreadFactor = pow(max(distortedAngle, 0.0), 1.0 / max(lightSpread, 0.001));

  float distance = length(sourceToCoord);
  float maxDistance = iResolution.x * rayLength;
  float lengthFalloff = clamp((maxDistance - distance) / maxDistance, 0.0, 1.0);
  
  float fadeFalloff = clamp((iResolution.x * fadeDistance - distance) / (iResolution.x * fadeDistance), 0.5, 1.0);
  float pulse = pulsating > 0.5 ? (0.8 + 0.2 * sin(iTime * speed * 3.0)) : 1.0;

  float baseStrength = clamp(
    (0.45 + 0.15 * sin(distortedAngle * seedA + iTime * speed)) +
    (0.3 + 0.2 * cos(-distortedAngle * seedB + iTime * speed)),
    0.0, 1.0
  );

  return baseStrength * lengthFalloff * fadeFalloff * spreadFactor * pulse;
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  vec2 coord = vec2(fragCoord.x, iResolution.y - fragCoord.y);
  
  vec2 finalRayDir = rayDir;
  if (mouseInfluence > 0.0) {
    vec2 mouseScreenPos = mousePos * iResolution.xy;
    vec2 mouseDirection = normalize(mouseScreenPos - rayPos);
    finalRayDir = normalize(mix(rayDir, mouseDirection, mouseInfluence));
  }

  vec4 rays1 = vec4(1.0) *
               rayStrength(rayPos, finalRayDir, coord, 36.2214, 21.11349,
                           1.5 * raysSpeed);
  vec4 rays2 = vec4(1.0) *
               rayStrength(rayPos, finalRayDir, coord, 22.3991, 18.0234,
                           1.1 * raysSpeed);

  fragColor = rays1 * 0.5 + rays2 * 0.4;

  if (noiseAmount > 0.0) {
    float n = noise(coord * 0.01 + iTime * 0.1);
    fragColor.rgb *= (1.0 - noiseAmount + noiseAmount * n);
  }

  float brightness = 1.0 - (coord.y / iResolution.y);
  fragColor.x *= 0.1 + brightness * 0.8;
  fragColor.y *= 0.3 + brightness * 0.6;
  fragColor.z *= 0.5 + brightness * 0.5;

  if (saturation != 1.0) {
    float gray = dot(fragColor.rgb, vec3(0.299, 0.587, 0.114));
    fragColor.rgb = mix(vec3(gray), fragColor.rgb, saturation);
  }

  fragColor.rgb *= raysColor;
  
  // Convert background/dark areas to be fully transparent, keep only rays
  float luminance = dot(fragColor.rgb, vec3(0.299, 0.587, 0.114));
  // Use smoothstep to completely cut off the dark haze threshold (e.g. < 0.35)
  // This essentially acts as a brightness filter, keeping only the intense rays
  fragColor.a = smoothstep(0.35, 0.85, luminance);
  // Optionally premultiply alpha to ensure smooth DOM compositing
  fragColor.rgb *= fragColor.a;
}

void main() {
  vec4 color;
  mainImage(color, gl_FragCoord.xy);
  gl_FragColor  = color;
}`;

      const uniforms = {
        iTime: { value: 0 },
        iResolution: { value: [1, 1] },

        rayPos: { value: [0, 0] },
        rayDir: { value: [0, 1] },

        raysColor: { value: hexToRgb(props.raysColor) },
        raysSpeed: { value: props.raysSpeed },
        lightSpread: { value: props.lightSpread },
        rayLength: { value: props.rayLength },
        pulsating: { value: props.pulsating ? 1.0 : 0.0 },
        fadeDistance: { value: props.fadeDistance },
        saturation: { value: props.saturation },
        mousePos: { value: [0.5, 0.5] },
        mouseInfluence: { value: props.mouseInfluence },
        noiseAmount: { value: props.noiseAmount },
        distortion: { value: props.distortion }
      };
      uniformsRef.value = uniforms;

      const geometry = new Triangle(gl);
      const program = new Program(gl, {
        vertex: vert,
        fragment: frag,
        uniforms,
        transparent: true
      });
      const mesh = new Mesh(gl, { geometry, program });
      meshRef.value = mesh;

      const updatePlacement = () => {
        if (!containerRef.value || !renderer) return;

        renderer.dpr = Math.min(window.devicePixelRatio, 2);

        const { clientWidth: wCSS, clientHeight: hCSS } = containerRef.value;
        renderer.setSize(wCSS, hCSS);

        const dpr = renderer.dpr;
        const w = wCSS * dpr;
        const h = hCSS * dpr;

        uniforms.iResolution.value = [w, h];

        const { anchor, dir } = getAnchorAndDir(props.raysOrigin, w, h);
        uniforms.rayPos.value = anchor;
        uniforms.rayDir.value = dir;
      };

      const loop = t => {
        if (!rendererRef.value || !uniformsRef.value || !meshRef.value) {
          return;
        }

        uniforms.iTime.value = t * 0.001;

        if (props.followMouse && props.mouseInfluence > 0.0) {
          const smoothing = 0.92;

          smoothMouseRef.value.x = smoothMouseRef.value.x * smoothing + mouseRef.value.x * (1 - smoothing);
          smoothMouseRef.value.y = smoothMouseRef.value.y * smoothing + mouseRef.value.y * (1 - smoothing);

          uniforms.mousePos.value = [smoothMouseRef.value.x, smoothMouseRef.value.y];
        }

        try {
          renderer.render({ scene: mesh });
          animationIdRef.value = requestAnimationFrame(loop);
        } catch (error) {
          console.warn('WebGL rendering error:', error);
          return;
        }
      };

      window.addEventListener('resize', updatePlacement);
      updatePlacement();
      animationIdRef.value = requestAnimationFrame(loop);

      cleanupFunctionRef.value = () => {
        if (animationIdRef.value) {
          cancelAnimationFrame(animationIdRef.value);
          animationIdRef.value = null;
        }

        window.removeEventListener('resize', updatePlacement);

        if (renderer) {
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
            console.warn('Error during WebGL cleanup:', error);
          }
        }

        rendererRef.value = null;
        uniformsRef.value = null;
        meshRef.value = null;
      };
    };

    initializeWebGL();
  },
  { deep: false, immediate: true }
);

watch(
  () => [
    props.raysColor,
    props.raysSpeed,
    props.lightSpread,
    props.raysOrigin,
    props.rayLength,
    props.pulsating,
    props.fadeDistance,
    props.saturation,
    props.mouseInfluence,
    props.noiseAmount,
    props.distortion
  ],
  () => {
    if (!uniformsRef.value || !containerRef.value || !rendererRef.value) return;

    const u = uniformsRef.value;
    const renderer = rendererRef.value;

    u.raysColor.value = hexToRgb(props.raysColor);
    u.raysSpeed.value = props.raysSpeed;
    u.lightSpread.value = props.lightSpread;
    u.rayLength.value = props.rayLength;
    u.pulsating.value = props.pulsating ? 1.0 : 0.0;
    u.fadeDistance.value = props.fadeDistance;
    u.saturation.value = props.saturation;
    u.mouseInfluence.value = props.mouseInfluence;
    u.noiseAmount.value = props.noiseAmount;
    u.distortion.value = props.distortion;

    const { clientWidth: wCSS, clientHeight: hCSS } = containerRef.value;
    const dpr = renderer.dpr;
    const { anchor, dir } = getAnchorAndDir(props.raysOrigin, wCSS * dpr, hCSS * dpr);
    u.rayPos.value = anchor;
    u.rayDir.value = dir;
  },
  { deep: false }
);

onMounted(() => {
  const handleMouseMove = e => {
    if (!containerRef.value || !rendererRef.value) return;
    const rect = containerRef.value.getBoundingClientRect();
    const x = (e.clientX - rect.left) / rect.width;
    const y = (e.clientY - rect.top) / rect.height;
    mouseRef.value = { x, y };
  };

  if (props.followMouse) {
    window.addEventListener('mousemove', handleMouseMove);
  }
  
  onBeforeUnmount(() => {
    window.removeEventListener('mousemove', handleMouseMove);
  });
});
</script>

<style scoped>
.light-rays-container {
  overflow: hidden;
  background: transparent;
}
</style>
