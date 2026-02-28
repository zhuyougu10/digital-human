import service from './request'

/**
 * 创建对话会话
 * @param {Object} data { sessionType }
 */
export function createSession(data) {
  return service({
    url: '/api/ai/chat/session',
    method: 'post',
    data
  })
}

/**
 * 获取会话列表
 */
export function getSessionList() {
  return service({
    url: '/api/ai/chat/sessions',
    method: 'get'
  })
}

/**
 * 删除会话
 * @param {Long} sessionId 
 */
export function deleteSession(sessionId) {
  return service({
    url: `/api/ai/chat/session/${sessionId}`,
    method: 'delete'
  })
}

/**
 * 获取会话消息列表
 * @param {Long} sessionId 
 */
export function getMessageList(sessionId) {
  return service({
    url: `/api/ai/chat/session/${sessionId}/messages`,
    method: 'get'
  })
}

/**
 * 发送对话消息 (SSE)
 * @param {Object} data { sessionId, message }
 */
export function sendMessage(data) {
  const token = localStorage.getItem('token')
  const baseURL = import.meta.env.VITE_API_BASE_URL || ''
  return fetch(`${baseURL}/api/ai/chat/send`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify(data)
  })
}

/**
 * 根据会话ID获取摘要
 * @param {Long} sessionId 
 */
export function getSummaryBySessionId(sessionId) {
  return service({
    url: `/api/ai/summary/session/${sessionId}`,
    method: 'get'
  })
}

/**
 * 根据预约ID获取摘要
 * @param {Long} appointmentId 
 */
export function getSummaryByAppointmentId(appointmentId) {
  return service({
    url: `/api/ai/summary/appointment/${appointmentId}`,
    method: 'get'
  })
}

/**
 * 创建百科会话
 */
export function createEncyclopediaSession() {
  return service({
    url: '/api/ai/encyclopedia/session',
    method: 'post'
  })
}

/**
 * 百科对话 (SSE)
 * @param {Object} data { sessionId, message }
 */
export function encyclopediaChat(data) {
  const token = localStorage.getItem('token')
  const baseURL = import.meta.env.VITE_API_BASE_URL || ''
  return fetch(`${baseURL}/api/ai/encyclopedia/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify(data)
  })
}
