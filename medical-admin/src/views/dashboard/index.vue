<template>
  <div class="dashboard-container">
    <div class="page-header">
      <h2 class="page-title">{{ isAdmin ? '运营数据看板' : '医生工作台' }}</h2>
      <span class="page-subtitle">{{ currentDate }}</span>
    </div>

    <!-- Statistics Cards -->
    <el-row :gutter="24">
      <el-col :xs="24" :sm="12" :md="6" v-for="item in stats" :key="item.title">
        <el-card shadow="hover" class="stats-card">
          <div class="stats-content">
            <div class="stats-info">
              <div class="stats-title">{{ item.title }}</div>
              <div class="stats-value">
                <count-to :startVal="0" :endVal="item.value" :duration="2000" />
                {{ item.value }}
              </div>
            </div>
            <div class="stats-icon-wrapper" :style="{ background: item.bgColor, color: item.color }">
              <el-icon><component :is="item.icon" /></el-icon>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Charts and Tables -->
    <el-row :gutter="24" class="mt-6">
      <el-col :span="isAdmin ? 16 : 24">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">{{ isAdmin ? '预约趋势 (最近7日)' : '今日接诊列表' }}</span>
              <el-tag v-if="!isAdmin" type="primary" effect="plain">{{ currentDate }}</el-tag>
            </div>
          </template>
          
          <div v-if="isAdmin" ref="trendChart" class="chart-container"></div>
          
          <el-table 
            v-else 
            :data="todayAppointments" 
            style="width: 100%" 
            v-loading="loading"
            :header-cell-style="{ background: '#F7F8FA', color: '#1F2937' }"
          >
            <el-table-column prop="patientName" label="患者姓名" width="120" />
            <el-table-column prop="time" label="预约时间" width="180" />
            <el-table-column prop="status" label="状态">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)" effect="light" round>{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" align="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="handleCheck(row)">
                  查看详情
                </el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="今日暂无预约" :image-size="100" />
            </template>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="8" v-if="isAdmin">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">科室预约占比</span>
            </div>
          </template>
          <div ref="pieChart" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, onUnmounted, nextTick } from 'vue'
import { useUserStore } from '@/stores/user'
import { getStatistics, getDoctorTodayAppointments } from '@/api/appointment'
import * as echarts from 'echarts'
import { 
  User, 
  Calendar, 
  ChatLineRound, 
  Files,
  List
} from '@element-plus/icons-vue'
import dayjs from 'dayjs'

const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)
const loading = ref(false)
const stats = ref([])
const todayAppointments = ref([])
const trendChart = ref(null)
const pieChart = ref(null)
let trendChartInstance = null
let pieChartInstance = null

const currentDate = dayjs().format('YYYY-MM-DD')

const initAdminDashboard = async () => {
  try {
    const res = await getStatistics()
    const data = res.data
    
    // Using medical palette
    stats.value = [
      { title: '今日预约数', value: data.todayAppointments, icon: Calendar, color: '#1677FF', bgColor: '#E6F4FF' },
      { title: '活跃用户', value: data.activeUsers, icon: User, color: '#52C41A', bgColor: '#F6FFED' },
      { title: 'AI对话量', value: data.aiChats, icon: ChatLineRound, color: '#FAAD14', bgColor: '#FFFBE6' },
      { title: '知识库条目', value: data.knowledgeEntries, icon: Files, color: '#FF4D4F', bgColor: '#FFF1F0' }
    ]

    await nextTick()
    initTrendChart(data.trendData)
    initPieChart(data.departmentData)
  } catch (error) {
    console.error('Failed to load admin stats:', error)
  }
}

const initDoctorDashboard = async () => {
  loading.value = true
  try {
    const res = await getDoctorTodayAppointments()
    todayAppointments.value = res.data.records || res.data.list || []
    
    stats.value = [
      { title: '今日预约数', value: res.data.total || 0, icon: Calendar, color: '#1677FF', bgColor: '#E6F4FF' },
      { title: '待接诊数', value: res.data.pending || 0, icon: List, color: '#52C41A', bgColor: '#F6FFED' }
    ]
  } catch (error) {
    console.error('Failed to load doctor appointments:', error)
  } finally {
    loading.value = false
  }
}

const initTrendChart = (data) => {
  if (!trendChart.value) return
  trendChartInstance = echarts.init(trendChart.value)
  const option = {
    grid: { top: 30, right: 20, bottom: 20, left: 40, containLabel: true },
    tooltip: { 
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.9)',
      borderColor: '#E5E7EB',
      textStyle: { color: '#1F2937' }
    },
    xAxis: {
      type: 'category',
      data: data.dates,
      axisLine: { lineStyle: { color: '#E5E7EB' } },
      axisLabel: { color: '#6B7280' }
    },
    yAxis: { 
      type: 'value',
      splitLine: { lineStyle: { type: 'dashed', color: '#F3F4F6' } }
    },
    series: [{
      data: data.values,
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 8,
      itemStyle: { color: '#1677FF' },
      lineStyle: { width: 3, color: '#1677FF' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(22, 119, 255, 0.2)' },
          { offset: 1, color: 'rgba(22, 119, 255, 0)' }
        ])
      }
    }]
  }
  trendChartInstance.setOption(option)
}

const initPieChart = (data) => {
  if (!pieChart.value) return
  pieChartInstance = echarts.init(pieChart.value)
  const option = {
    tooltip: { trigger: 'item' },
    legend: { bottom: '0', left: 'center', itemWidth: 10, itemHeight: 10 },
    color: ['#1677FF', '#52C41A', '#FAAD14', '#FF4D4F', '#722ED1', '#13C2C2'],
    series: [{
      name: '科室占比',
      type: 'pie',
      radius: ['45%', '70%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } },
      data: data
    }]
  }
  pieChartInstance.setOption(option)
}

const getStatusType = (status) => {
  const map = {
    '待就诊': 'primary',
    '已完成': 'success',
    '已取消': 'info'
  }
  return map[status] || 'info'
}

const handleCheck = (row) => {
  // Navigation logic here if needed
  console.log('Checking appointment:', row)
}

const handleResize = () => {
  trendChartInstance?.resize()
  pieChartInstance?.resize()
}

onMounted(() => {
  if (isAdmin.value) {
    initAdminDashboard()
  } else {
    initDoctorDashboard()
  }
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  trendChartInstance?.dispose()
  pieChartInstance?.dispose()
})
</script>

<style scoped>
.dashboard-container {
  padding: 0;
}

.page-header {
  margin-bottom: 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.page-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
}

.stats-card {
  border: none;
  border-radius: var(--radius-md);
  margin-bottom: 20px;
  transition: transform 0.2s;
}

.stats-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.stats-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stats-title {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.stats-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1;
}

.stats-icon-wrapper {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.chart-card {
  border: none;
  border-radius: var(--radius-md);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.chart-container {
  height: 360px;
}

.mt-6 {
  margin-top: 24px;
}
</style>
