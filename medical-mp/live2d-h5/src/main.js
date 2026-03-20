import * as PIXI from 'pixi.js'
window.PIXI = PIXI
import { playAuthenticatedAudio, unlockAudioPlayback } from './audio-player'
import VConsole from 'vconsole'

if (import.meta.env.DEV && !window.__H5_VCONSOLE__) {
  window.__H5_VCONSOLE__ = new VConsole()
}

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
  let sessionId = urlParams.get('sessionId')
  const rawApiBase = urlParams.get('apiBase') || 'http://localhost:8080/api'
  const resolveApiBase = (baseUrl) => {
    try {
      const parsed = new URL(baseUrl)
      if (parsed.hostname === 'localhost' || parsed.hostname === '127.0.0.1') {
        parsed.hostname = window.location.hostname
      }
      return parsed.toString().replace(/\/$/, '')
    } catch (error) {
      console.warn('[H5] apiBase 解析失败，使用原始值:', { baseUrl, error })
      return baseUrl
    }
  }
  const apiBase = resolveApiBase(rawApiBase)
  if (token && sessionId) {
    console.log('[H5] 接收到鉴权信息:', { sessionId, rawApiBase, apiBase })
  }

  // --- TTS Sequential Playback State ---
  let ttsQueue = []          // [{segmentIndex, ttsUrl, fileName}]
  let ttsPlaying = false
  let ttsTotalSegments = 0
  let currentPlayIndex = 0
  let playedFileNames = []   // 已播放完的文件名，用于最终清理

  const resetTtsState = () => {
    ttsQueue = []
    ttsPlaying = false
    ttsTotalSegments = 0
    currentPlayIndex = 0
    playedFileNames = []
  }

  const playNextTtsSegment = async () => {
    if (ttsPlaying) return
    
    const next = ttsQueue.find(item => item.segmentIndex === currentPlayIndex)
    if (!next) return
    
    ttsPlaying = true
    const audioUrl = next.ttsUrl.startsWith('http') ? next.ttsUrl : (apiBase + next.ttsUrl)
    
    try {
      await playAuthenticatedAudio({
        audioUrl,
        token,
        onLoadedMetadata: (audio) => {
          if (lipSyncManager) lipSyncManager.start(audio.duration * 1000)
        }
      })
    } catch (e) {
      console.warn('[H5] TTS 段落播放失败:', next.segmentIndex, e)
    } finally {
      playedFileNames.push(next.fileName)
      ttsPlaying = false
      currentPlayIndex++
      
      if (currentPlayIndex >= ttsTotalSegments && ttsTotalSegments > 0) {
        if (lipSyncManager) lipSyncManager.stop()
        cleanupTtsAudio()
        // 不在这里重置，防止后续事件到达
      } else {
        playNextTtsSegment()
      }
    }
  }

  const cleanupTtsAudio = async () => {
    if (playedFileNames.length === 0) return
    try {
      await fetch(`${apiBase}/ai/chat/tts/cleanup`, {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(playedFileNames)
      })
      console.log('[H5] TTS 音频清理完成:', playedFileNames.length, '个文件')
    } catch (e) {
      console.warn('[H5] TTS 音频清理失败:', e)
    }
  }

  // --- 历史记录与分页状态 ---
  let remainingMessages = []
  let isLoadingHistory = false
  const BATCH_SIZE = 20

  const prependMessage = (role, content) => {
    const msgDiv = document.createElement('div')
    msgDiv.className = `msg ${role}`
    const bubbleDiv = document.createElement('div')
    bubbleDiv.className = 'bubble'
    
    if (role === 'ai' && content) {
      bubbleDiv.innerHTML = window.marked ? window.marked.parse(content) : content
    } else {
      bubbleDiv.innerText = content
    }
    
    msgDiv.appendChild(bubbleDiv)
    // 插入到最顶部，但要在 load-more-tip 之后
    const tip = messageList.querySelector('.load-more-tip')
    if (tip) {
      tip.after(msgDiv)
    } else {
      messageList.prepend(msgDiv)
    }
    return bubbleDiv
  }

  const loadMoreMessages = () => {
    if (remainingMessages.length === 0) {
      const tip = messageList.querySelector('.load-more-tip')
      if (tip) tip.innerText = '已加载全部历史消息'
      return
    }

    const scrollHeightBefore = messageList.scrollHeight
    const batch = remainingMessages.splice(-BATCH_SIZE).reverse() // 取最后20个并反转顺序

    batch.forEach(msg => {
      prependMessage(msg.role === 'assistant' ? 'ai' : 'user', msg.content)
    })

    // 恢复滚动位置
    requestAnimationFrame(() => {
      messageList.scrollTop = messageList.scrollHeight - scrollHeightBefore
    })
  }

  const loadHistory = async () => {
    if (!sessionId || !token || isLoadingHistory) return
    isLoadingHistory = true
    
    try {
      const res = await fetch(`${apiBase}/ai/chat/session/${sessionId}/messages`, {
        headers: { 'Authorization': `Bearer ${token}` }
      })
      if (!res.ok) return
      const result = await res.json()
      const messages = result.data || result
      if (!Array.isArray(messages) || messages.length === 0) return

      // 如果有历史消息，先清空默认欢迎语
      messageList.innerHTML = ''
      
      // 添加加载提示
      const tip = document.createElement('div')
      tip.className = 'load-more-tip'
      tip.innerText = messages.length > BATCH_SIZE ? '上滑加载历史消息' : '已加载全部历史消息'
      messageList.appendChild(tip)

      // 拆分消息：最近20条立即渲染，其余存入 remainingMessages
      const total = messages.length
      const initial = messages.slice(Math.max(0, total - BATCH_SIZE))
      remainingMessages = messages.slice(0, Math.max(0, total - BATCH_SIZE))

      initial.forEach(msg => {
        appendMessage(msg.role === 'assistant' ? 'ai' : 'user', msg.content)
      })

      // 滚动到底部
      messageList.scrollTop = messageList.scrollHeight
    } catch (e) {
      console.warn('[H5] 加载历史消息失败:', e)
    } finally {
      isLoadingHistory = false
    }
  }

  // 监听滚动加载更多
  messageList.addEventListener('scroll', () => {
    if (messageList.scrollTop < 50 && !isLoadingHistory && remainingMessages.length > 0) {
      loadMoreMessages()
    }
  })

  // 新会话按钮逻辑
  const newChatBtn = document.getElementById('new-chat-btn')
  if (newChatBtn) {
    newChatBtn.addEventListener('click', async () => {
      if (!confirm('确定要开始新的问诊会话吗？')) return
      
      try {
        statusText.innerHTML = '<div id="status-dot"></div> 正在创建新会话...'
        // 结束当前会话
        await fetch(`${apiBase}/ai/chat/session/${sessionId}/end`, {
          method: 'POST',
          headers: { 'Authorization': `Bearer ${token}` }
        })
        
        // 创建新会话
        const res = await fetch(`${apiBase}/ai/chat/session`, {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ sessionType: 'TRIAGE' })
        })
        const result = await res.json()
        const newSessionId = result.data?.id || result.id
        
        // 更新全局 sessionId 和状态
        sessionId = newSessionId
        remainingMessages = []
        
        // 清空消息列表并显示欢迎语
        messageList.innerHTML = ''
        appendMessage('ai', '您好！我是AI医疗助手，请描述您的症状，我来帮您分析和推荐合适的医生。')
        
        // 通知 UniApp 更新 sessionId（可选）
        if (window.uni && window.uni.postMessage) {
          window.uni.postMessage({ data: { type: 'SESSION_CHANGED', sessionId: newSessionId } })
        }
      } catch (e) {
        console.error('[H5] 创建新会话失败:', e)
        alert('创建新会话失败，请重试')
      } finally {
        statusText.innerHTML = '<div id="status-dot"></div> 正在为您服务...'
      }
    })
  }

  let currentAiBubble = null
  let currentFullText = ''
  let renderPending = false
  let currentAbortController = null

  const enhanceBubbleWithCards = (bubbleDiv, text) => {
    // 匹配模式：推荐医生[：: ]*姓名[：: ]*(.+?)[，, ]+科室[：: ]*(.+?)[，, ]+擅长[：: ]*(.+?)(?=\n|$)
    const doctorRegex = /(?:推荐医生|为您推荐|以下医生)[：: ]*(.+?)[，, ]+科室[：: ]*(.+?)[，, ]+擅长[：: ]*(.+?)(?=\n|$)/g
    let match
    let hasCard = false
    
    while ((match = doctorRegex.exec(text)) !== null) {
      const [_, name, dept, specialty] = match
      const cardHtml = `
        <div class="doctor-card">
          <div class="doctor-card-header">
            <div class="doctor-avatar">👨‍⚕️</div>
            <div class="doctor-info">
              <div class="doctor-name">${name.trim()}</div>
              <div class="doctor-dept">${dept.trim()}</div>
            </div>
          </div>
          <div class="doctor-card-body">
            <div class="doctor-specialty">擅长：${specialty.trim()}</div>
          </div>
        </div>
      `
      const cardWrapper = document.createElement('div')
      cardWrapper.innerHTML = cardHtml
      bubbleDiv.appendChild(cardWrapper.firstElementChild)
      hasCard = true
    }
    
    if (hasCard) {
      messageList.scrollTop = messageList.scrollHeight
    }
  }

  const appendMessage = (role, content) => {
    const msgDiv = document.createElement('div')
    msgDiv.className = `msg ${role}`
    const bubbleDiv = document.createElement('div')
    bubbleDiv.className = 'bubble'
    
    if (role === 'ai' && content) {
      bubbleDiv.innerHTML = window.marked ? window.marked.parse(content) : content
    } else {
      bubbleDiv.innerText = content
    }
    
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

    // 取消旧请求
    if (currentAbortController) {
      currentAbortController.abort()
    }
    currentAbortController = new AbortController()

    // 重置 TTS 状态
    resetTtsState()

    // 显示 AI 正在思考
    currentFullText = ''
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
        body: JSON.stringify({ sessionId: Number(sessionId), message: text }),
        signal: currentAbortController.signal
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      const updateAiBubble = () => {
        if (!currentAiBubble) return
        currentAiBubble.innerHTML = window.marked ? window.marked.parse(currentFullText) : currentFullText
        messageList.scrollTop = messageList.scrollHeight
        renderPending = false
      }

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
              // 修复 SSE data 行解析，保留空格
              dataContent = line.startsWith('data: ') ? line.substring(6) : line.substring(5)
            }
          }
          if (dataContent === '[DONE]') continue

          try {
            const payload = JSON.parse(dataContent)
            if (payload.type === 'token' && currentAiBubble) {
              currentFullText += payload.content || ''
              if (!renderPending) {
                renderPending = true
                requestAnimationFrame(updateAiBubble)
              }
            } else if (payload.type === 'tts') {
              // 处理分段 TTS
              if (payload.ttsUrl) {
                const fileName = payload.ttsUrl.split('/').pop()
                ttsQueue.push({
                  segmentIndex: payload.segmentIndex || 0,
                  ttsUrl: payload.ttsUrl,
                  fileName: fileName
                })
                ttsTotalSegments = payload.totalSegments || 1
                
                // 保证顺序并尝试播放
                ttsQueue.sort((a, b) => a.segmentIndex - b.segmentIndex)
                playNextTtsSegment()
              }
            } else if (payload.type === 'tts_error') {
              console.warn('[H5] TTS 合成失败:', payload.content)
            } else if (payload.type === 'complete') {
              // 确保最终渲染完整内容
              updateAiBubble()
              // 增强展示卡片
              enhanceBubbleWithCards(currentAiBubble, currentFullText)
            } else if (payload.type === 'error' && currentAiBubble) {
              currentFullText += payload.content || '服务暂时不可用'
              updateAiBubble()
            }
          } catch (e) {
            // 非 JSON，当作纯文本 token
            if (currentAiBubble) {
              currentFullText += dataContent
              if (!renderPending) {
                renderPending = true
                requestAnimationFrame(updateAiBubble)
              }
            }
          }
        }
      }
    } catch (e) {
      if (e.name === 'AbortError') {
        console.log('[H5] 请求已取消')
        return
      }
      console.error('[H5] SSE 请求失败:', e)
      if (currentAiBubble && !currentFullText) {
        currentFullText = '抱歉，服务暂时不可用，请稍后重试'
        updateAiBubble()
      }
    }

    // 恢复状态
    statusText.innerHTML = '<div id="status-dot"></div> 正在为您服务...'
    currentAiBubble = null
    currentFullText = ''
    currentAbortController = null
  }

  const handleSend = () => {
    const text = chatInput.value.trim()
    if (!text) return

    unlockAudioPlayback().catch(err => {
      if (err?.name !== 'NotSupportedError') {
        console.warn('[H5] 音频解锁失败:', {
          name: err?.name,
          message: err?.message,
          stack: err?.stack
        })
      }
    })
    
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

  // 初始化加载历史记录
  loadHistory()
}

bootstrap()
