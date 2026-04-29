import { resolveApiBase } from '../../shared/runtime-config'

const BASE_URL = resolveApiBase()

const concatUint8Arrays = (left, right) => {
  if (!left?.length) return right
  if (!right?.length) return left
  const combined = new Uint8Array(left.length + right.length)
  combined.set(left, 0)
  combined.set(right, left.length)
  return combined
}

const getUtf8SequenceLength = (byte) => {
  if ((byte & 0x80) === 0) return 1
  if ((byte & 0xe0) === 0xc0) return 2
  if ((byte & 0xf0) === 0xe0) return 3
  if ((byte & 0xf8) === 0xf0) return 4
  return 1
}

const splitUtf8Bytes = (bytes) => {
  if (!bytes?.length) {
    return {
      complete: new Uint8Array(0),
      pending: new Uint8Array(0)
    }
  }

  let pendingLength = 0
  for (let i = bytes.length - 1; i >= 0 && i >= bytes.length - 4; i -= 1) {
    const byte = bytes[i]
    if ((byte & 0xc0) === 0x80) {
      pendingLength += 1
      continue
    }
    const expectedLength = getUtf8SequenceLength(byte)
    if (expectedLength === 1) {
      pendingLength = 0
    } else if (pendingLength + 1 < expectedLength) {
      pendingLength += 1
    } else {
      pendingLength = 0
    }
    break
  }

  if (pendingLength === 0) {
    return {
      complete: bytes,
      pending: new Uint8Array(0)
    }
  }

  return {
    complete: bytes.slice(0, bytes.length - pendingLength),
    pending: bytes.slice(bytes.length - pendingLength)
  }
}

const toUint8Array = (chunk) => {
  if (chunk instanceof ArrayBuffer) return new Uint8Array(chunk)
  if (ArrayBuffer.isView(chunk)) {
    return new Uint8Array(chunk.buffer, chunk.byteOffset, chunk.byteLength)
  }
  return null
}

const normalizeChunkText = (chunk, pendingBytes) => {
  if (!chunk) {
    return {
      text: '',
      pendingBytes
    }
  }
  if (typeof chunk === 'string') return chunk

  const byteChunk = toUint8Array(chunk)
  if (byteChunk) {
    const combined = concatUint8Arrays(pendingBytes, byteChunk)
    const { complete, pending } = splitUtf8Bytes(combined)
    return {
      text: decodeUTF8(complete),
      pendingBytes: pending
    }
  }

  return {
    text: String(chunk),
    pendingBytes
  }
}

const consumeSseText = (state, incomingText) => {
  const normalized = incomingText.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  let lineBuffer = state.lineBuffer + normalized
  const events = []

  let newlineIndex = lineBuffer.indexOf('\n')
  while (newlineIndex !== -1) {
    const rawLine = lineBuffer.slice(0, newlineIndex)
    lineBuffer = lineBuffer.slice(newlineIndex + 1)
    const line = rawLine.trimEnd()

    if (line === '') {
      if (state.eventLines.length > 0) {
        events.push(parseSseBlock(state.eventLines))
        state.eventLines = []
      }
    } else {
      state.eventLines.push(line)
    }

    newlineIndex = lineBuffer.indexOf('\n')
  }

  state.lineBuffer = lineBuffer
  return events
}

const parseSseBlock = (lines) => {
  let eventType = 'message'
  const dataLines = []

  for (const line of lines) {
    if (!line || line.startsWith(':')) continue
    if (line.startsWith('event:')) {
      eventType = line.substring(6).trim()
    } else if (line.startsWith('data:')) {
      const value = line.startsWith('data: ') ? line.substring(6) : line.substring(5)
      dataLines.push(value)
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
  const sseState = {
    eventLines: [],
    lineBuffer: ''
  }
  let pendingBytes = new Uint8Array(0)
  let completed = false

  const completeOnce = () => {
    if (completed) return
    completed = true
    onComplete && onComplete()
  }

  const failOnce = (error) => {
    if (completed) return
    completed = true
    onError && onError(error)
  }

  const dispatchChunk = (chunk) => {
    const normalized = normalizeChunkText(chunk, pendingBytes)
    if (typeof normalized === 'string') {
      pendingBytes = new Uint8Array(0)
      processText(normalized)
      return
    }
    pendingBytes = normalized.pendingBytes
    processText(normalized.text)
  }

  const processText = (text) => {
    if (!text) return
    const events = consumeSseText(sseState, text)
    for (const event of events) {
      if (event.dataContent === '[DONE]') {
        completeOnce()
        return
      }
      if (event.dataContent) {
        onMessage && onMessage(event.eventType, event.dataContent)
      }
    }
  }

  const flushPending = () => {
    if (pendingBytes.length > 0) {
      processText(decodeUTF8(pendingBytes))
      pendingBytes = new Uint8Array(0)
    }
    if (sseState.lineBuffer) {
      processText('\n')
    }
    if (sseState.eventLines.length > 0) {
      const event = parseSseBlock(sseState.eventLines)
      sseState.eventLines = []
      if (event.dataContent) {
        onMessage && onMessage(event.eventType, event.dataContent)
      }
    }
  }
  
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
      flushPending()
      if (res.statusCode >= 200 && res.statusCode < 300) {
        completeOnce()
      } else {
        failOnce(new Error(`HTTP Error ${res.statusCode}`))
      }
    },
    fail: (err) => {
      failOnce(err)
    }
  })

  requestTask.onChunkReceived((res) => {
    dispatchChunk(res.data)
  })
  
  return requestTask
}
