const BASE_URL = import.meta.env.VITE_API_BASE || 'http://192.168.31.210:8080/api'

const normalizeChunkText = (chunk) => {
  if (!chunk) return ''
  if (typeof chunk === 'string') return chunk
  if (chunk instanceof ArrayBuffer) return decodeUTF8(chunk)
  if (ArrayBuffer.isView(chunk)) {
    return decodeUTF8(chunk.buffer.slice(chunk.byteOffset, chunk.byteOffset + chunk.byteLength))
  }
  return String(chunk)
}

const splitSseBlocks = (buffer) => {
  const normalized = buffer.replace(/\r\n/g, '\n')
  const blocks = normalized.split(/\n\n/)
  return {
    blocks: blocks.slice(0, -1),
    rest: blocks[blocks.length - 1] || ''
  }
}

const parseSseBlock = (block) => {
  let eventType = 'message'
  const dataLines = []

  for (const rawLine of block.split('\n')) {
    const line = rawLine.trimEnd()
    if (!line || line.startsWith(':')) continue
    if (line.startsWith('event:')) {
      eventType = line.substring(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.substring(5).trim())
    }
  }

  return {
    eventType,
    dataContent: dataLines.join('\n')
  }
}

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
    responseType: 'arraybuffer',
    header: {
      'Authorization': token ? `Bearer ${token}` : '',
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream'
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
    const chunk = normalizeChunkText(res.data)
    buffer += chunk

    const { blocks, rest } = splitSseBlocks(buffer)
    buffer = rest

    for (const block of blocks) {
      if (!block.trim()) continue
      const { eventType, dataContent } = parseSseBlock(block)
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
