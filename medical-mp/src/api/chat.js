import request from './request'

export const createSession = (type) => {
  return request({
    url: '/ai/chat/session',
    method: 'POST',
    data: { type }
  })
}

export const getSessionList = () => {
  return request({
    url: '/ai/chat/session/list',
    method: 'GET'
  })
}

export const getMessageList = (sessionId) => {
  return request({
    url: `/ai/chat/session/${sessionId}/messages`,
    method: 'GET'
  })
}
