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
  const apiBase = urlParams.get('apiBase') || 'http://localhost:8080/api'
  if (token && sessionId) {
    console.log('[H5] 接收到鉴权信息:', { sessionId, apiBase })
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

  const sendToBackend = async (text) => {
    if (!sessionId || !token) {
      console.error('[H5] 缺少 sessionId 或 token，无法发送消息')
      return
    }

    // 显示 AI 正在思考
    currentAiBubble = appendMessage('ai', '')
    statusText.innerHTML = '<div id="status-dot"></div> 正在思考...'

    try {
      const response = await fetch(`${apiBase}/ai/chat/send`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream'
        },
        body: JSON.stringify({ sessionId: Number(sessionId), message: text })
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const blocks = buffer.split('\n\n')
        buffer = blocks.pop() // 最后一个可能不完整

        for (const block of blocks) {
          if (!block.trim()) continue
          let eventType = 'message'
          let dataContent = ''
          for (const line of block.split('\n')) {
            if (line.startsWith('event:')) {
              eventType = line.substring(6).trim()
            } else if (line.startsWith('data:')) {
              dataContent = line.substring(5).trim()
            }
          }
          if (dataContent === '[DONE]') continue
          if (!dataContent) continue

          try {
            const payload = JSON.parse(dataContent)
            if (payload.type === 'token' && currentAiBubble) {
              currentAiBubble.innerText += payload.content || ''
              messageList.scrollTop = messageList.scrollHeight
            } else if (payload.type === 'complete') {
              // 处理语音播放和口型同步
              if (payload.ttsUrl) {
                // 如果 ttsUrl 是相对路径，拼接 apiBase 构建完整 URL
                const audioUrl = payload.ttsUrl.startsWith('http') ? payload.ttsUrl : (apiBase + payload.ttsUrl)
                const audio = new Audio(audioUrl)
                audio.onloadedmetadata = () => {
                  if (lipSyncManager) lipSyncManager.start(audio.duration * 1000)
                  audio.play().catch(err => console.error('[H5] 播放音频失败:', err))
                }
                audio.onerror = (e) => {
                  console.error('[H5] 音频加载失败:', e)
                }
              }
            } else if (payload.type === 'error' && currentAiBubble) {
              currentAiBubble.innerText += payload.content || '服务暂时不可用'
            }
          } catch (e) {
            // 非 JSON，当作纯文本 token
            if (currentAiBubble) {
              currentAiBubble.innerText += dataContent
              messageList.scrollTop = messageList.scrollHeight
            }
          }
        }
      }
    } catch (e) {
      console.error('[H5] SSE 请求失败:', e)
      if (currentAiBubble && !currentAiBubble.innerText) {
        currentAiBubble.innerText = '抱歉，服务暂时不可用，请稍后重试'
      }
    }

    // 恢复状态
    statusText.innerHTML = '<div id="status-dot"></div> 正在为您服务...'
    currentAiBubble = null
  }

  const handleSend = () => {
    const text = chatInput.value.trim()
    if (!text) return
    
    appendMessage('user', text)
    chatInput.value = ''
    
    // 直接通过 fetch SSE 调用后端（绕过小程序 postMessage 不实时的限制）
    sendToBackend(text)
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
