import * as PIXI from 'pixi.js'
window.PIXI = PIXI

// [Codex 降级接管] Solution A: Full H5 Chat UI with Live2D.
async function bootstrap() {
  const { Application } = PIXI
  const { Live2DManager } = await import('./live2d-manager')
  const { LipSyncManager } = await import('./tts-lip-sync')

  const appRoot = document.getElementById('live2d-canvas-container')
  
  const app = new Application({
    transparent: true, // v6: make canvas transparent
    backgroundAlpha: 0, // v7+: make background transparent
    antialias: true,
    resizeTo: appRoot, // Fit only the top zone
    autoStart: true
  })

  // Explicitly ensure transparency for older/newer Pixi versions
  // Use try-catch or checks to avoid read-only property errors in different Pixi versions
  if (app.renderer) {
    try {
      // For Pixi v6, backgroundAlpha is a property
      app.renderer.backgroundAlpha = 0
    } catch (e) {
      // Ignore if read-only or not supported
    }
    
    try {
      // clearBeforeRender might be read-only in some versions
      // Default is usually true, so we can skip setting it if it fails
      app.renderer.clearBeforeRender = true
    } catch (e) {
      // Ignore
    }
  }

  if (appRoot) {
    appRoot.appendChild(app.view)
  }

  const live2dManager = new Live2DManager(app)
  const model = await live2dManager.load('/models/doctor/wariza.model3.json')
  const lipSyncManager = new LipSyncManager(() => live2dManager.getModel())

  app.ticker.add(() => lipSyncManager.update())
  if (model) live2dManager.playMotion('Idle')

  // --- Chat UI Logic ---
  const messageList = document.getElementById('message-list')
  const chatInput = document.getElementById('chat-input')
  const sendBtn = document.getElementById('send-btn')
  const statusText = document.getElementById('status-text')

  let currentAiBubble = null

  const appendMessage = (role, content) => {
    const msgDiv = document.createElement('div')
    msgDiv.className = `msg ${role}`
    const bubbleDiv = document.createElement('div')
    bubbleDiv.className = 'bubble'
    bubbleDiv.innerText = content
    msgDiv.appendChild(bubbleDiv)
    messageList.appendChild(msgDiv)
    messageList.scrollTop = messageList.scrollHeight
    return bubbleDiv
  }

  const handleSend = () => {
    const text = chatInput.value.trim()
    if (!text) return
    
    appendMessage('user', text)
    chatInput.value = ''
    
    // Notify UniApp
    window.parent.postMessage({
      type: 'USER_SEND',
      data: text
    }, '*')
    
    // Fallback for some environments
    if (window.uni && window.uni.postMessage) {
      window.uni.postMessage({ data: { type: 'USER_SEND', content: text } })
    }
  }

  sendBtn.addEventListener('click', handleSend)
  chatInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') handleSend()
  })

  // Handle messages from UniApp
  window.addEventListener('message', (event) => {
    const { type, data } = event.data || {}
    
    switch (type) {
      case 'AI_START':
        currentAiBubble = appendMessage('ai', '')
        statusText.innerText = '正在思考...'
        break
      case 'AI_TOKEN':
        if (currentAiBubble) {
          currentAiBubble.innerText += data
          messageList.scrollTop = messageList.scrollHeight
        }
        break
      case 'AI_COMPLETE':
        statusText.innerText = '正在为您服务...'
        break
      case 'START_LIPSYNC':
        lipSyncManager.start(data?.duration || 0)
        break
      case 'STOP_LIPSYNC':
        lipSyncManager.stop()
        break
      default:
        break
    }
  })

  window.addEventListener('resize', () => live2dManager.fitToScreen())
}

bootstrap()
