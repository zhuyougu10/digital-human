<template>
  <div class="patient-summary-page" v-loading="loading">
    <el-card shadow="never" class="header-card">
      <div class="header-row">
        <div>
          <h2>患者对话摘要</h2>
          <p class="sub-title">预约ID：{{ appointmentId || '-' }}</p>
        </div>
        <el-button @click="goBack">返回预约列表</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="m-t-20">
      <template #header>
        <div class="card-title">结构化摘要</div>
      </template>

      <el-empty v-if="!hasSummary" description="暂无摘要数据" :image-size="90" />

      <el-descriptions v-else :column="2" border>
        <el-descriptions-item label="主诉" :span="2">{{ summaryData.chiefComplaint || '-' }}</el-descriptions-item>
        <el-descriptions-item label="伴随症状" :span="2">{{ summaryData.symptoms || '-' }}</el-descriptions-item>
        <el-descriptions-item label="持续时间">{{ summaryData.duration || '-' }}</el-descriptions-item>
        <el-descriptions-item label="严重程度">
          <el-tag :type="getSeverityType(summaryData.severity)">{{ summaryData.severity || '-' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="既往史" :span="2">{{ summaryData.pastHistory || '-' }}</el-descriptions-item>
        <el-descriptions-item label="AI判断" :span="2">{{ summaryData.aiDiagnosis || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" class="m-t-20">
      <template #header>
        <div class="card-title">完整对话记录</div>
      </template>

      <el-empty v-if="!messageList.length" description="暂无对话记录" :image-size="90" />

      <el-collapse v-else v-model="activeCollapse">
        <el-collapse-item name="chat-record">
          <template #title>
            <span>点击展开/收起对话（共 {{ messageList.length }} 条）</span>
          </template>
          <div class="message-list">
            <div
              v-for="item in messageList"
              :key="item.id || `${item.role}-${item.createTime}`"
              class="message-item"
              :class="isUserMessage(item) ? 'user' : 'assistant'"
            >
              <div class="message-header">
                <el-tag size="small" :type="isUserMessage(item) ? 'primary' : 'success'">
                  {{ isUserMessage(item) ? '患者' : 'AI助手' }}
                </el-tag>
                <span class="message-time">{{ item.createTime || item.sendTime || '-' }}</span>
              </div>
              <div class="message-content">{{ item.content || item.message || '-' }}</div>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAppointmentById } from '@/api/appointment'
import { getSummaryByAppointmentId, getMessageList } from '@/api/chat'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const messageList = ref([])
const activeCollapse = ref(['chat-record'])
const summaryData = reactive({
  chiefComplaint: '',
  symptoms: '',
  duration: '',
  severity: '',
  pastHistory: '',
  aiDiagnosis: '',
  sessionId: null
})

const appointmentId = computed(() => route.params.id || route.query.id || '')
const hasSummary = computed(() => {
  return Object.entries(summaryData)
    .filter(([key]) => key !== 'sessionId')
    .some(([, value]) => !!value)
})

const getSeverityType = (severity) => {
  const map = {
    轻度: 'success',
    中度: 'warning',
    重度: 'danger',
    LOW: 'success',
    MEDIUM: 'warning',
    HIGH: 'danger'
  }
  return map[severity] || 'info'
}

const isUserMessage = (item) => {
  const role = String(item.role || item.messageRole || item.sender || '').toLowerCase()
  return role.includes('user') || role.includes('patient') || role.includes('human')
}

const extractSummary = (raw = {}) => {
  const data = raw.data || raw
  const detail = data.summary || data.result || data

  let parsed = {}
  if (typeof detail === 'string') {
    try {
      parsed = JSON.parse(detail)
    } catch (error) {
      parsed = {}
    }
  } else {
    parsed = detail || {}
  }

  return {
    chiefComplaint: parsed.chiefComplaint || parsed.mainComplaint || parsed.complaint || '',
    symptoms: parsed.symptoms || parsed.accompanyingSymptoms || '',
    duration: parsed.duration || parsed.lasted || '',
    severity: parsed.severity || parsed.level || '',
    pastHistory: parsed.pastHistory || parsed.history || '',
    aiDiagnosis: parsed.aiDiagnosis || parsed.diagnosis || parsed.conclusion || '',
    sessionId: data.sessionId || parsed.sessionId || data.chatSessionId || null
  }
}

const fetchSummary = async () => {
  if (!appointmentId.value) return
  const res = await getSummaryByAppointmentId(appointmentId.value)
  Object.assign(summaryData, extractSummary(res))
}

const fetchSessionMessages = async (sessionId) => {
  if (!sessionId) {
    messageList.value = []
    return
  }
  const res = await getMessageList(sessionId)
  const data = res.data || {}
  messageList.value = Array.isArray(data) ? data : (data.list || [])
}

const fetchFallbackByAppointment = async () => {
  if (!appointmentId.value) return
  const res = await getAppointmentById(appointmentId.value)
  const detail = res.data || {}
  const fallback = {
    chiefComplaint: detail.chiefComplaint || '',
    symptoms: detail.symptoms || '',
    duration: detail.duration || '',
    severity: detail.severity || '',
    pastHistory: detail.pastHistory || '',
    aiDiagnosis: detail.aiDiagnosis || '',
    sessionId: detail.sessionId || detail.chatSessionId || null
  }

  if (!hasSummary.value) {
    Object.assign(summaryData, fallback)
  }

  if (!summaryData.sessionId && fallback.sessionId) {
    summaryData.sessionId = fallback.sessionId
  }
}

const loadData = async () => {
  if (!appointmentId.value) return
  loading.value = true
  try {
    await fetchSummary()
    await fetchFallbackByAppointment()
    await fetchSessionMessages(summaryData.sessionId)
  } catch (error) {
    console.error('Failed to load patient summary:', error)
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  router.push('/doctor/appointments')
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.patient-summary-page {
  padding: 20px;
}
.header-card {
  margin-bottom: 20px;
}
.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.sub-title {
  margin: 8px 0 0;
  color: #909399;
}
.m-t-20 {
  margin-top: 20px;
}
.card-title {
  font-weight: 600;
}
.message-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.message-item {
  border-radius: 8px;
  padding: 12px;
}
.message-item.user {
  background: #ecf5ff;
  border: 1px solid #d9ecff;
}
.message-item.assistant {
  background: #f0f9eb;
  border: 1px solid #e1f3d8;
}
.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.message-time {
  color: #909399;
  font-size: 12px;
}
.message-content {
  line-height: 1.7;
  white-space: pre-wrap;
}
</style>
