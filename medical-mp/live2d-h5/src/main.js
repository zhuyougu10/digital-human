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

  // 解析 URL 参数获取鉴权信息 (供后续 SSE 扩展使用)
  const urlParams = new URLSearchParams(window.location.search)
  const token = urlParams.get('token')
  const sessionId = urlParams.get('sessionId')
  if (token && sessionId) {
    console.log('[H5] 接收到鉴权信息:', { sessionId })
  }

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

  // 统一消息处理函数
  const handleUniAppMessage = (payload) => {
    const { type, data } = payload || {}
    
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
  }

  // 监听标准 postMessage (App/H5 模式)
  window.addEventListener('message', (event) => {
    handleUniAppMessage(event.data)
  })

  // 监听 hash 变更 (微信小程序/兼容模式)
  window.addEventListener('hashchange', () => {
    const hash = window.location.hash
    if (hash.startsWith('#msg=')) {
      try {
        const payload = JSON.parse(decodeURIComponent(hash.substring(5)))
        handleUniAppMessage(payload)
      } catch (e) {
        console.error('[H5] 解析 Hash 消息失败:', e)
      }
    }
  })

  window.addEventListener('resize', () => live2dManager.fitToScreen())
}

bootstrap()
