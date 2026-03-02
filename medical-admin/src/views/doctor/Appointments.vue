<template>
  <div class="doctor-appointments">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="就诊日期">
          <el-date-picker
            v-model="searchForm.date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择预约日期"
            @change="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="m-t-20">
      <el-table :data="appointmentList" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="预约ID" width="100" />
        <el-table-column label="患者昵称" min-width="140">
          <template #default="{ row }">
            {{ row.patientNickname || row.patientName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="预约时间" min-width="180">
          <template #default="{ row }">
            {{ formatAppointmentTime(row) }}
          </template>
        </el-table-column>
        <el-table-column label="排队号" width="100">
          <template #default="{ row }">
            {{ row.queueNo || row.queueNumber || row.serialNo || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="goToSummary(row)">查看对话摘要</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="searchForm.pageNum"
          v-model:page-size="searchForm.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getDoctorAppointments } from '@/api/appointment'

const router = useRouter()
const loading = ref(false)
const appointmentList = ref([])
const total = ref(0)

const today = new Date().toISOString().slice(0, 10)

const searchForm = reactive({
  pageNum: 1,
  pageSize: 10,
  date: today
})

const statusMap = {
  PENDING: { label: '待就诊', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  CANCELLED: { label: '已取消', type: 'info' },
  EXPIRED: { label: '已过期', type: 'warning' },
  待就诊: { label: '待就诊', type: 'primary' },
  已完成: { label: '已完成', type: 'success' },
  已取消: { label: '已取消', type: 'info' }
}

const buildParams = () => {
  const params = {
    pageNum: searchForm.pageNum,
    pageSize: searchForm.pageSize
  }
  if (searchForm.date) {
    params.date = searchForm.date
  }
  return params
}

const fetchAppointments = async () => {
  loading.value = true
  try {
    const res = await getDoctorAppointments(buildParams())
    const data = res.data || {}
    if (Array.isArray(data)) {
      appointmentList.value = data
      total.value = data.length
    } else {
      appointmentList.value = data.list || []
      total.value = data.total || 0
    }
  } catch (error) {
    console.error('Failed to fetch my appointments:', error)
  } finally {
    loading.value = false
  }
}

const formatAppointmentTime = (row) => {
  if (row.appointmentTime) return row.appointmentTime
  const date = row.appointmentDate || row.date || ''
  const slot = row.timeSlot || row.slotTime || ''
  return `${date} ${slot}`.trim() || '-'
}

const getStatusType = (status) => statusMap[status]?.type || 'info'
const getStatusLabel = (status) => statusMap[status]?.label || status || '-'

const goToSummary = (row) => {
  if (!row.id) return
  router.push(`/doctor/patient-summary/${row.id}`)
}

const handleSearch = () => {
  searchForm.pageNum = 1
  fetchAppointments()
}

const resetSearch = () => {
  searchForm.date = today
  searchForm.pageNum = 1
  searchForm.pageSize = 10
  fetchAppointments()
}

const handleSizeChange = (val) => {
  searchForm.pageSize = val
  fetchAppointments()
}

const handleCurrentChange = (val) => {
  searchForm.pageNum = val
  fetchAppointments()
}

onMounted(() => {
  fetchAppointments()
})
</script>

<style scoped>
.doctor-appointments {
  padding: 20px;
}
.search-card {
  margin-bottom: 20px;
}
.m-t-20 {
  margin-top: 20px;
}
.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
