const BASE_URL = import.meta.env.VITE_API_BASE || 'http://localhost:9090/api'

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
    
    for (const line of lines) {
      if (line.startsWith('data:')) {
        const content = line.substring(5).trim()
        if (content === '[DONE]') {
          onComplete && onComplete()
          return
        }
        
        try {
          // Some SSE events might be raw strings, others JSON
          // We'll pass the whole thing and let the component decide
          onMessage && onMessage('message', content)
        } catch (e) {
          console.error('Failed to parse SSE line', e)
        }
      }
    }
  })
  
  return requestTask
}
