import { reactive, ref, onMounted, onUnmounted } from 'vue'

const GRID_SIZE = 20
const TILE_SIZE = 20
const INIT_SPEED = 180
const MIN_SPEED = 60
const SPEED_STEP = 2
const SCORE_STEP = 10

export function useSnakeGame() {
  const canvasRef = ref(null)

  const gameState = reactive({
    status: 'start',
    score: 0,
    speed: INIT_SPEED
  })

  let snake = []
  let direction = { x: 0, y: 0 }
  let nextDirection = { x: 0, y: 0 }
  let food = { x: 0, y: 0 }
  let loopTimeoutId = null

  const initSnake = () => {
    snake = [
      { x: 10, y: 10 },
      { x: 10, y: 11 },
      { x: 10, y: 12 }
    ]
    direction = { x: 0, y: -1 }
    nextDirection = { x: 0, y: -1 }
  }

  const generateFood = () => {
    let newFood
    let isOnSnake = true
    while (isOnSnake) {
      newFood = {
        x: Math.floor(Math.random() * GRID_SIZE),
        y: Math.floor(Math.random() * GRID_SIZE)
      }
      isOnSnake = snake.some(segment => segment.x === newFood.x && segment.y === newFood.y)
    }
    food = newFood
  }

  const draw = () => {
    const canvas = canvasRef.value
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    const width = canvas.width
    const height = canvas.height

    ctx.fillStyle = '#f8fafc'
    ctx.fillRect(0, 0, width, height)

    ctx.strokeStyle = '#e2e8f0'
    ctx.lineWidth = 0.5
    for (let i = 0; i <= GRID_SIZE; i++) {
      ctx.beginPath()
      ctx.moveTo(i * TILE_SIZE, 0)
      ctx.lineTo(i * TILE_SIZE, height)
      ctx.stroke()
      ctx.beginPath()
      ctx.moveTo(0, i * TILE_SIZE)
      ctx.lineTo(width, i * TILE_SIZE)
      ctx.stroke()
    }

    ctx.fillStyle = '#ef4444'
    ctx.beginPath()
    const foodX = food.x * TILE_SIZE + TILE_SIZE / 2
    const foodY = food.y * TILE_SIZE + TILE_SIZE / 2
    ctx.arc(foodX, foodY, TILE_SIZE / 2 - 2, 0, Math.PI * 2)
    ctx.fill()

    snake.forEach((segment, index) => {
      ctx.fillStyle = index === 0 ? '#10b981' : '#34d399'
      const x = segment.x * TILE_SIZE
      const y = segment.y * TILE_SIZE

      ctx.beginPath()
      ctx.roundRect(x + 1, y + 1, TILE_SIZE - 2, TILE_SIZE - 2, 4)
      ctx.fill()
    })
  }

  const update = () => {
    if (gameState.status !== 'playing') return

    direction = nextDirection
    const head = snake[0]
    const nextHead = {
      x: head.x + direction.x,
      y: head.y + direction.y
    }

    if (
      nextHead.x < 0 ||
      nextHead.x >= GRID_SIZE ||
      nextHead.y < 0 ||
      nextHead.y >= GRID_SIZE
    ) {
      return gameOver()
    }

    if (snake.some(segment => segment.x === nextHead.x && segment.y === nextHead.y)) {
      return gameOver()
    }

    snake.unshift(nextHead)

    if (nextHead.x === food.x && nextHead.y === food.y) {
      gameState.score += SCORE_STEP
      gameState.speed = Math.max(MIN_SPEED, gameState.speed - SPEED_STEP)
      generateFood()
    } else {
      snake.pop()
    }

    draw()

    loopTimeoutId = setTimeout(update, gameState.speed)
  }

  const handleKeydown = (e) => {
    if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', ' '].includes(e.key)) {
      e.preventDefault()
    }

    if (gameState.status !== 'playing') return

    switch (e.key) {
      case 'ArrowUp':
      case 'w':
      case 'W':
        if (direction.y !== 1) nextDirection = { x: 0, y: -1 }
        break
      case 'ArrowDown':
      case 's':
      case 'S':
        if (direction.y !== -1) nextDirection = { x: 0, y: 1 }
        break
      case 'ArrowLeft':
      case 'a':
      case 'A':
        if (direction.x !== 1) nextDirection = { x: -1, y: 0 }
        break
      case 'ArrowRight':
      case 'd':
      case 'D':
        if (direction.x !== -1) nextDirection = { x: 1, y: 0 }
        break
    }
  }

  const startGame = () => {
    if (loopTimeoutId) clearTimeout(loopTimeoutId)
    initSnake()
    generateFood()
    gameState.score = 0
    gameState.speed = INIT_SPEED
    gameState.status = 'playing'
    update()
  }

  const gameOver = () => {
    gameState.status = 'gameover'
    if (loopTimeoutId) clearTimeout(loopTimeoutId)
  }

  onMounted(() => {
    window.addEventListener('keydown', handleKeydown)
    draw()
  })

  onUnmounted(() => {
    window.removeEventListener('keydown', handleKeydown)
    if (loopTimeoutId) clearTimeout(loopTimeoutId)
  })

  return {
    canvasRef,
    gameState,
    startGame
  }
}
