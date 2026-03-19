const BASE_URL = import.meta.env.VITE_API_BASE || 'http://localhost:8080/api'

export const createSSERequest = (url, data, callbacks) => {
  const { onMessage, onComplete, onError } = callbacks
  const token = uni.getStorageSync('token')
  
  const requestTask = uni.request({
    url: BASE_URL + url,
    method: 'POST',
    data,
    header: {
      'Authorization': token ? `Bearer ${token}` : '',
      'Content-Type': 'application/json'
    },
    enableChunked: true,
    success: (res) => {
      if (res.statusCode >= 200 && res.statusCode < 300) {
        onComplete && onComplete()
      } else {
        onError && onError(new Error(`HTTP Error ${res.statusCode}`))
      }
    },
    fail: (err) => {
      onError && onError(err)
    }
  })

  let buffer = ''
  requestTask.onChunkReceived((res) => {
    // ArrayBuffer to string conversion
    const chunk = String.fromCharCode.apply(null, new Uint8Array(res.data))
    buffer += chunk
    
    const lines = buffer.split('\n\n')
    buffer = lines.pop() // Last one might be incomplete
    
    for (const block of lines) {
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
      if (dataContent === '[DONE]') {
        onComplete && onComplete()
        return
      }
      if (dataContent) {
        onMessage && onMessage(eventType, dataContent)
      }
    }
  })
  
  return requestTask
}
