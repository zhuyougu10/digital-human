<template>
  <div class="appointment-management">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            @change="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="选择状态" clearable style="width: 150px">
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
    </el-card>

    <el-card shadow="never" class="m-t-20">
      <el-table :data="appointmentList" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="预约ID" width="100" />
        <el-table-column label="患者" min-width="120">
          <template #default="{ row }">
            {{ row.patientName }} ({{ row.patientPhone }})
          </template>
        </el-table-column>
        <el-table-column label="医生" min-width="120">
          <template #default="{ row }">
            {{ row.doctorName }}
          </template>
        </el-table-column>
        <el-table-column prop="departmentName" label="科室" width="120" />
        <el-table-column label="预约时间" width="200">
          <template #default="{ row }">
            {{ row.appointmentDate }} {{ row.timeSlot }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              type="danger"
              link
              @click="handleCancel(row)"
            >取消</el-button>
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
        />
      </div>
    </el-card>

    <!-- Detail Dialog -->
    <el-dialog v-model="detailDialog.visible" title="预约详情" width="500px">
      <el-descriptions :column="1" border v-if="detailDialog.data">
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
import { ElMessage, ElMessageBox } from 'element-plus'

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
    appointmentList.value = res.data.list
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
    await ElMessageBox.confirm('确定要取消该预约吗？', '提示', { type: 'warning' })
    await cancelAppointment(row.id)
    ElMessage.success('取消成功')
    fetchAppointmentList()
  } catch (error) {
    if (error !== 'cancel') console.error('Failed to cancel appointment:', error)
  }
}

const getStatusType = (status) => {
  const map = {
    'PENDING': 'primary',
    'COMPLETED': 'success',
    'CANCELLED': 'info',
    'EXPIRED': 'warning'
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
