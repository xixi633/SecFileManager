<template>
  <div class="snake-wrapper">
    <div class="game-card">
      <div class="game-header">
        <h2 class="title">贪吃蛇大作战</h2>
        <div class="score-board">
          <span class="label">SCORE</span>
          <span class="value">{{ gameState.score }}</span>
        </div>
      </div>

      <div class="canvas-container">
        <canvas ref="canvasRef" width="400" height="400" class="game-canvas"></canvas>

        <div v-if="gameState.status === 'start'" class="overlay start-screen">
          <div class="instructions">
            <p>使用 <strong>方向键</strong> 或 <strong>W/A/S/D</strong> 控制移动</p>
          </div>
          <button class="btn btn-primary" @click="startGame">
            开始游戏
          </button>
        </div>

        <div v-if="gameState.status === 'gameover'" class="overlay gameover-screen">
          <h3 class="gameover-title">GAME OVER</h3>
          <p class="final-score">最终得分: {{ gameState.score }}</p>
          <button class="btn btn-restart" @click="startGame">
            再来一局
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useSnakeGame } from '../composables/useSnakeGame.js'

const { canvasRef, gameState, startGame } = useSnakeGame()
</script>

<style scoped>
.snake-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  background-color: #f1f5f9;
  font-family: 'Helvetica Neue', Arial, sans-serif;
  padding: 20px;
}

.game-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.game-header {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title {
  margin: 0;
  font-size: 1.5rem;
  color: #1e293b;
  font-weight: 700;
}

.score-board {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  padding: 6px 16px;
  border-radius: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.score-board .label {
  font-size: 0.75rem;
  font-weight: 600;
  color: #64748b;
  letter-spacing: 1px;
}

.score-board .value {
  font-size: 1.25rem;
  font-weight: 700;
  color: #ef4444;
}

.canvas-container {
  position: relative;
  width: 400px;
  height: 400px;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: inset 0 2px 4px 0 rgba(0, 0, 0, 0.05);
  background-color: #f8fafc;
  border: 2px solid #e2e8f0;
}

.game-canvas {
  display: block;
  width: 100%;
  height: 100%;
}

.overlay {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(4px);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  animation: fadeIn 0.3s ease-in-out;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.instructions {
  margin-bottom: 24px;
  color: #475569;
  font-size: 0.95rem;
}

.instructions strong {
  color: #1e293b;
  background: #e2e8f0;
  padding: 2px 6px;
  border-radius: 4px;
}

.gameover-title {
  font-size: 2.5rem;
  margin: 0 0 10px 0;
  color: #ef4444;
  font-weight: 900;
  letter-spacing: 2px;
}

.final-score {
  font-size: 1.2rem;
  color: #334155;
  margin-bottom: 30px;
  font-weight: 500;
}

.btn {
  padding: 12px 32px;
  font-size: 1.1rem;
  font-weight: 600;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn:active {
  transform: scale(0.96);
}

.btn-primary {
  background-color: #3b82f6;
  color: white;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.btn-primary:hover {
  background-color: #2563eb;
  box-shadow: 0 6px 16px rgba(59, 130, 246, 0.4);
}

.btn-restart {
  background-color: #10b981;
  color: white;
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);
}

.btn-restart:hover {
  background-color: #059669;
  box-shadow: 0 6px 16px rgba(16, 185, 129, 0.4);
}
</style>
