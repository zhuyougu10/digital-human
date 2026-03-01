import * as PIXI from 'pixi.js'
import { Live2DManager } from './live2d-manager'
import { LipSyncManager } from './tts-lip-sync'

// [Codex 降级接管] Bootstraps Pixi + Live2D runtime for mini-program web-view embedding.
async function bootstrap() {
  window.PIXI = PIXI

  const app = new PIXI.Application({
    backgroundAlpha: 0,
    antialias: true,
    resizeTo: window
  })

  const appRoot = document.getElementById('app')
  appRoot.appendChild(app.view)

  const live2dManager = new Live2DManager(app)
  await live2dManager.load('./models/doctor/doctor.model3.json')

  const lipSyncManager = new LipSyncManager(() => live2dManager.getModel())

  const handleResize = () => {
    live2dManager.fitToScreen()
  }

  window.addEventListener('resize', handleResize)

  window.addEventListener('message', (event) => {
    const rawData = event?.data
    const payload = Array.isArray(rawData) ? rawData[0] || {} : rawData || {}
    const { type, data = {} } = payload

    switch (type) {
      case 'START_LIPSYNC':
        lipSyncManager.start()
        break
      case 'STOP_LIPSYNC':
        lipSyncManager.stop()
        break
      case 'PLAY_MOTION':
        live2dManager.playMotion(data.group, data.index ?? 0)
        break
      case 'SET_EXPRESSION':
        live2dManager.setExpression(data.expression)
        break
      default:
        break
    }
  })
}

bootstrap()
