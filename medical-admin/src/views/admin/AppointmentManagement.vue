<template>
  <div class="appointment-management">
    <el-card shadow="hover" class="main-card">
      <template #header>
        <div class="card-header">
          <div class="left-panel">
            <span class="title">预约管理</span>
          </div>
          <div class="right-panel">
            <el-form :inline="true" :model="searchForm" class="search-form">
              <el-form-item>
                <el-date-picker
                  v-model="searchForm.dateRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  value-format="YYYY-MM-DD"
                  @change="handleSearch"
                  class="date-picker"
                />
              </el-form-item>
              <el-form-item>
                <el-select v-model="searchForm.status" placeholder="全部状态" clearable class="status-select">
                  <el-option label="待就诊" value="PENDING" />
                  <el-option label="已完成" value="COMPLETED" />
                  <el-option label="已取消" value="CANCELLED" />
                  <el-option label="已过期" value="EXPIRED" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleSearch">查询</el-button>
                <el-button @click="resetSearch">重置</el-button>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </template>

      <el-table 
        :data="appointmentList" 
        v-loading="loading" 
        style="width: 100%"
        :header-cell-style="{ background: '#F7F8FA', color: '#1F2937', fontWeight: '600' }"
      >
        <el-table-column prop="id" label="ID" width="100" align="center" />
        <el-table-column label="患者信息" min-width="180">
          <template #default="{ row }">
            <div class="patient-info">
              <span class="name">{{ row.patientName }}</span>
              <span class="phone">{{ row.patientPhone }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="医生" min-width="150">
          <template #default="{ row }">
            <span class="doctor-name">{{ row.doctorName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="departmentName" label="科室" width="120" />
        <el-table-column label="预约时间" width="220">
          <template #default="{ row }">
            <div class="time-info">
              <el-icon class="mr-1"><Calendar /></el-icon>
              <span>{{ row.appointmentDate }} {{ row.timeSlot }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" effect="light" round>
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewDetail(row)">详情</el-button>
            <el-popconfirm 
              v-if="row.status === 'PENDING'"
              title="确定要取消该预约吗？" 
              @confirm="handleCancel(row)"
              width="200px"
            >
              <template #reference>
                <el-button type="danger" link>取消</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="searchForm.pageNum"
          v-model:page-size="searchForm.pageSize"
          layout="total, sizes, prev, pager, next"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          background
        />
      </div>
    </el-card>

    <!-- Detail Dialog -->
    <el-dialog v-model="detailDialog.visible" title="预约详情" width="500px" class="custom-dialog">
      <el-descriptions :column="1" border v-if="detailDialog.data" class="detail-desc">
        <el-descriptions-item label="预约ID">{{ detailDialog.data.id }}</el-descriptions-item>
        <el-descriptions-item label="患者姓名">{{ detailDialog.data.patientName }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detailDialog.data.patientPhone }}</el-descriptions-item>
        <el-descriptions-item label="预约医生">{{ detailDialog.data.doctorName }}</el-descriptions-item>
        <el-descriptions-item label="所属科室">{{ detailDialog.data.departmentName }}</el-descriptions-item>
        <el-descriptions-item label="预约日期">{{ detailDialog.data.appointmentDate }}</el-descriptions-item>
        <el-descriptions-item label="时间段">{{ detailDialog.data.timeSlot }}</el-descriptions-item>
        <el-descriptions-item label="预约状态">
          <el-tag :type="getStatusType(detailDialog.data.status)">
            {{ getStatusLabel(detailDialog.data.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailDialog.data.createTime }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailDialog.visible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getAppointmentList, getAppointmentById, cancelAppointment } from '@/api/appointment'
import { ElMessage } from 'element-plus'
import { Calendar } from '@element-plus/icons-vue'

const loading = ref(false)
const appointmentList = ref([])
const total = ref(0)

const searchForm = reactive({
  pageNum: 1,
  pageSize: 10,
  dateRange: [],
  status: ''
})

const detailDialog = reactive({
  visible: false,
  data: null
})

const fetchAppointmentList = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: searchForm.pageNum,
      pageSize: searchForm.pageSize,
      status: searchForm.status
    }
    if (searchForm.dateRange?.length === 2) {
      params.startDate = searchForm.dateRange[0]
      params.endDate = searchForm.dateRange[1]
    }
    const res = await getAppointmentList(params)
    appointmentList.value = res.data.records
    total.value = res.data.total
  } catch (error) {
    console.error('Failed to fetch appointments:', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  searchForm.pageNum = 1
  fetchAppointmentList()
}

const resetSearch = () => {
  searchForm.dateRange = []
  searchForm.status = ''
  handleSearch()
}

const handleSizeChange = (val) => {
  searchForm.pageSize = val
  fetchAppointmentList()
}

const handleCurrentChange = (val) => {
  searchForm.pageNum = val
  fetchAppointmentList()
}

const viewDetail = async (row) => {
  try {
    const res = await getAppointmentById(row.id)
    detailDialog.data = res.data
    detailDialog.visible = true
  } catch (error) {
    console.error('Failed to fetch detail:', error)
  }
}

const handleCancel = async (row) => {
  try {
    await cancelAppointment(row.id)
    ElMessage.success('取消成功')
    fetchAppointmentList()
  } catch (error) {
    if (error !== 'cancel') console.error('Failed to cancel appointment:', error)
  }
}

const getStatusType = (status) => {
  const map = {
    0: 'warning',
    1: 'success',
    2: 'danger',
    3: 'info',
    'PENDING': 'warning',
    'CONFIRMED': 'success',
    'CANCELLED': 'danger',
    'COMPLETED': 'success',
    'EXPIRED': 'info'
  }
  return map[status] || 'info'
}

const getStatusLabel = (status) => {
  const map = {
    'PENDING': '待就诊',
    'COMPLETED': '已完成',
    'CANCELLED': '已取消',
    'EXPIRED': '已过期'
  }
  return map[status] || status
}

onMounted(() => {
  fetchAppointmentList()
})
</script>

<style scoped>
.appointment-management {
}

.main-card {
  border: none;
  border-radius: var(--radius-md);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.search-form {
  display: flex;
  gap: 10px;
}

.search-form .el-form-item {
  margin-bottom: 0;
  margin-right: 0;
}

.date-picker {
  width: 240px;
}

.status-select {
  width: 120px;
}

.patient-info {
  display: flex;
  flex-direction: column;
}

.patient-info .name {
  font-weight: 500;
  color: var(--text-primary);
}

.patient-info .phone {
  font-size: 12px;
  color: var(--text-secondary);
}

.doctor-name {
  font-weight: 500;
  color: var(--text-primary);
}

.time-info {
  display: flex;
  align-items: center;
  color: var(--text-secondary);
  font-size: 13px;
}

.pagination-container {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}

/* Custom dialog styling */
.detail-desc :deep(.el-descriptions__label) {
  width: 100px;
  font-weight: 500;
}
</style>
