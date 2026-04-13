const BASE_URL = import.meta.env.VITE_API_BASE || 'http://192.168.31.210:8080/api'

// 小程序没有 TextDecoder，手写 UTF-8 解码
const decodeUTF8 = (bytes) => {
  const arr = new Uint8Array(bytes)
  let result = ''
  let i = 0
  while (i < arr.length) {
    const byte = arr[i]
    let code, bytesNeeded
    if (byte < 0x80) {
      code = byte; bytesNeeded = 0
    } else if (byte < 0xE0) {
      code = byte & 0x1F; bytesNeeded = 1
    } else if (byte < 0xF0) {
      code = byte & 0x0F; bytesNeeded = 2
    } else {
      code = byte & 0x07; bytesNeeded = 3
    }
    if (i + bytesNeeded >= arr.length) break // 不完整的多字节序列
    for (let j = 0; j < bytesNeeded; j++) {
      i++
      code = (code << 6) | (arr[i] & 0x3F)
    }
    if (code > 0xFFFF) {
      code -= 0x10000
      result += String.fromCharCode(0xD800 + (code >> 10), 0xDC00 + (code & 0x3FF))
    } else {
      result += String.fromCharCode(code)
    }
    i++
  }
  return result
}

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
    const chunk = decodeUTF8(res.data)
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
