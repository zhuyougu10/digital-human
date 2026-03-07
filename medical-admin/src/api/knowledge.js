import service from './request'

/**
 * 分页获取知识库列表
 * @param {Object} params pageQuery
 */
export function getKnowledgeBaseList(params) {
  return service({
    url: '/api/knowledge/kb/list',
    method: 'get',
    params
  })
}

/**
 * 创建知识库
 * @param {Object} data 
 */
export function createKnowledgeBase(data) {
  return service({
    url: '/api/knowledge/kb',
    method: 'post',
    data
  })
}

/**
 * 删除知识库
 * @param {Long} id 
 */
export function deleteKnowledgeBase(id) {
  return service({
    url: `/api/knowledge/kb/${id}`,
    method: 'delete'
  })
}

/**
 * 获取知识库文档列表
 * @param {Long} kbId 
 * @param {Object} params pageQuery
 */
export function getDocumentList(kbId, params) {
  return service({
    url: `/api/knowledge/kb/${kbId}/documents`,
    method: 'get',
    params
  })
}

/**
 * 上传文档
 * @param {Long} kbId 
 * @param {File} file 
 */
export function uploadDocument(kbId, file) {
  const formData = new FormData()
  formData.append('file', file)
  return service({
    url: `/api/knowledge/kb/${kbId}/document`,
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

/**
 * 删除文档
 * @param {Long} docId 
 */
export function deleteDocument(docId) {
  return service({
    url: `/api/knowledge/kb/document/${docId}`,
    method: 'delete'
  })
}

/**
 * 获取文档分片列表
 * @param {Long} docId 
 * @param {Object} params pageQuery
 */
export function getChunkList(docId, params) {
  return service({
    url: `/api/knowledge/kb/document/${docId}/chunks`,
    method: 'get',
    params
  })
}

/**
 * 添加手动分片
 * @param {Long} kbId 
 * @param {Object} data 
 */
export function addManualChunk(kbId, data) {
  return service({
    url: `/api/knowledge/kb/${kbId}/chunk`,
    method: 'post',
    data
  })
}

/**
 * 删除分片
 * @param {Long} chunkId 
 */
export function deleteChunk(chunkId) {
  return service({
    url: `/api/knowledge/kb/chunk/${chunkId}`,
    method: 'delete'
  })
}

/**
 * 搜索知识库
 * @param {Object} data { kbId, query, topK }
 */
export function searchKnowledge(data) {
  return service({
    url: '/api/knowledge/kb/search',
    method: 'post',
    data
  })
}

export function getManualChunkList(kbId, params) {
  return service({
    url: `/api/knowledge/kb/${kbId}/manual-chunks`,
    method: 'get',
    params
  })
}
