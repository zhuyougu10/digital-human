<template>
  <div class="dashboard-container">
    <!-- Statistics Cards -->
    <el-row :gutter="20">
      <el-col :xs="24" :sm="12" :md="6" v-for="item in stats" :key="item.title">
        <el-card shadow="hover" class="stats-card">
          <div class="stats-item">
            <div class="stats-icon" :style="{ backgroundColor: item.color }">
              <el-icon><component :is="item.icon" /></el-icon>
            </div>
            <div class="stats-info">
              <div class="stats-title">{{ item.title }}</div>
              <div class="stats-value">{{ item.value }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Charts and Tables -->
    <el-row :gutter="20" class="m-t-20">
      <el-col :span="isAdmin ? 16 : 24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>{{ isAdmin ? '预约趋势 (最近7日)' : '今日接诊列表' }}</span>
            </div>
          </template>
          
          <div v-if="isAdmin" ref="trendChart" class="chart-container"></div>
          
          <el-table v-else :data="todayAppointments" style="width: 100%" v-loading="loading">
            <el-table-column prop="patientName" label="患者姓名" />
            <el-table-column prop="time" label="预约时间" />
            <el-table-column prop="status" label="状态">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作">
              <template #default="{ row }">
                <el-button type="primary" link @click="handleCheck(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="8" v-if="isAdmin">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>科室预约占比</span>
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

const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)
const loading = ref(false)
const stats = ref([])
const todayAppointments = ref([])
const trendChart = ref(null)
const pieChart = ref(null)
let trendChartInstance = null
let pieChartInstance = null

const initAdminDashboard = async () => {
  try {
    const res = await getStatistics()
    const data = res.data
    
    stats.value = [
      { title: '今日预约数', value: data.todayAppointments, icon: Calendar, color: '#409EFF' },
      { title: '活跃用户', value: data.activeUsers, icon: User, color: '#67C23A' },
      { title: 'AI对话量', value: data.aiChats, icon: ChatLineRound, color: '#E6A23C' },
      { title: '知识库条目', value: data.knowledgeEntries, icon: Files, color: '#F56C6C' }
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
    todayAppointments.value = res.data.list
    
    stats.value = [
      { title: '今日预约数', value: res.data.total, icon: Calendar, color: '#409EFF' },
      { title: '待接诊数', value: res.data.pending, icon: List, color: '#67C23A' }
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
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: data.dates // ['02-22', '02-23', ...]
    },
    yAxis: { type: 'value' },
    series: [{
      data: data.values,
      type: 'line',
      smooth: true,
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(64, 158, 255, 0.5)' },
          { offset: 1, color: 'rgba(64, 158, 255, 0)' }
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
    legend: { bottom: '0', left: 'center' },
    series: [{
      name: '科室占比',
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      data: data // [{ value: 1048, name: '内科' }, ...]
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
  padding: 20px;
}
.m-t-20 {
  margin-top: 20px;
}
.stats-card {
  margin-bottom: 20px;
}
.stats-item {
  display: flex;
  align-items: center;
}
.stats-icon {
  width: 50px;
  height: 50px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 24px;
  margin-right: 15px;
}
.stats-title {
  font-size: 14px;
  color: #909399;
}
.stats-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
}
.chart-container {
  height: 350px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
