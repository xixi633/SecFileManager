<template>
  <div class="games-page">
    <div class="games-header">
      <h2 class="games-title">游戏中心</h2>
      <p class="games-subtitle">选择一款游戏开始畅玩</p>
    </div>
    <div class="games-grid">
      <div
        v-for="game in games"
        :key="game.route"
        class="game-card"
        @click="router.push(game.route)"
      >
        <div class="game-card-cover">
          <span class="game-card-icon">{{ game.icon }}</span>
        </div>
        <div class="game-card-info">
          <h3 class="game-card-name">{{ game.name }}</h3>
          <p class="game-card-desc">{{ game.description }}</p>
        </div>
        <div class="game-card-footer">
          <span class="game-card-tag">{{ game.tag }}</span>
          <span class="game-card-arrow">&rarr;</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const games = [
  {
    name: '贪吃蛇',
    description: '经典贪吃蛇游戏，用方向键控制蛇的移动，吃掉食物不断成长。',
    icon: '🐍',
    tag: '休闲',
    route: '/game/snake'
  }
]
</script>

<style scoped>
.games-page {
  height: 100%;
}

.games-header {
  margin-bottom: 24px;
}

.games-title {
  margin: 0 0 4px 0;
  font-size: 1.5rem;
  font-weight: 700;
  color: #1e293b;
}

.games-subtitle {
  margin: 0;
  font-size: 0.9rem;
  color: #94a3b8;
}

.games-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 20px;
}

.game-card {
  background: #ffffff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  display: flex;
  flex-direction: column;
}

.game-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.game-card-cover {
  height: 140px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.game-card-icon {
  font-size: 3.5rem;
}

.game-card-info {
  padding: 16px;
  flex: 1;
}

.game-card-name {
  margin: 0 0 6px 0;
  font-size: 1.1rem;
  font-weight: 600;
  color: #1e293b;
}

.game-card-desc {
  margin: 0;
  font-size: 0.85rem;
  color: #64748b;
  line-height: 1.5;
}

.game-card-footer {
  padding: 12px 16px;
  border-top: 1px solid #f1f5f9;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.game-card-tag {
  font-size: 0.75rem;
  background: #eff6ff;
  color: #3b82f6;
  padding: 2px 10px;
  border-radius: 10px;
  font-weight: 500;
}

.game-card-arrow {
  font-size: 1.1rem;
  color: #94a3b8;
  transition: transform 0.2s ease;
}

.game-card:hover .game-card-arrow {
  transform: translateX(4px);
  color: #3b82f6;
}
</style>
