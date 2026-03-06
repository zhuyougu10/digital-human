<template>
  <div class="doctor-management">
    <el-card shadow="hover" class="main-card">
      <template #header>
        <div class="card-header">
          <div class="left-panel">
            <span class="title">医生管理</span>
          </div>
          <div class="right-panel">
            <el-form :inline="true" :model="searchForm" class="search-form">
              <el-form-item>
                <el-input 
                  v-model="searchForm.keyword" 
                  placeholder="搜索姓名/职称" 
                  clearable 
                  @keyup.enter="handleSearch"
                  class="search-input"
                >
                  <template #prefix><el-icon><Search /></el-icon></template>
                </el-input>
              </el-form-item>
              <el-form-item>
                <el-select v-model="searchForm.departmentId" placeholder="选择科室" clearable class="dept-select">
                  <el-option
                    v-for="dept in departmentOptions"
                    :key="dept.id"
                    :label="dept.name"
                    :value="dept.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleSearch">查询</el-button>
                <el-button @click="resetSearch">重置</el-button>
              </el-form-item>
            </el-form>
            <div class="divider"></div>
            <el-button type="primary" icon="Plus" @click="handleAdd">新增医生</el-button>
          </div>
        </div>
      </template>

      <el-table 
        :data="doctorList" 
        v-loading="loading" 
        style="width: 100%"
        :header-cell-style="{ background: '#F7F8FA', color: '#1F2937', fontWeight: '600' }"
      >
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column label="姓名" width="180">
          <template #default="{ row }">
            <div class="doctor-info">
              <el-avatar :size="36" :src="row.avatar" class="mr-2">{{ row.name?.charAt(0) }}</el-avatar>
              <span class="doctor-name">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="职称" width="140">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" type="info">{{ row.title }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="所属科室" min-width="180">
          <template #default="{ row }">
            <div class="dept-tags">
              <el-tag 
                v-for="dept in row.departments" 
                :key="dept.id" 
                size="small" 
                class="dept-tag"
                effect="light"
              >
                {{ dept.name }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="specialties" label="擅长领域" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">
              <el-icon class="mr-1"><Edit /></el-icon>编辑
            </el-button>
            <el-button type="primary" link @click="goToSchedule(row)">
              <el-icon class="mr-1"><Calendar /></el-icon>排班
            </el-button>
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
          background
        />
      </div>
    </el-card>

    <!-- Add/Edit Dialog -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.isEdit ? '编辑医生' : '新增医生'"
      width="680px"
      class="custom-dialog"
      destroy-on-close
      @closed="resetDialog"
    >
      <el-form :model="dialog.form" :rules="rules" ref="formRef" label-width="90px" class="doctor-form">
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="关联用户" prop="userId">
              <el-select v-model="dialog.form.userId" filterable placeholder="请选择用户" style="width: 100%">
                <el-option
                  v-for="user in userOptions"
                  :key="user.id"
                  :label="`${user.username}${user.nickname ? ` (${user.nickname})` : ''}`"
                  :value="user.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名" prop="name">
              <el-input v-model="dialog.form.name" placeholder="请输入医生姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="职称" prop="title">
              <el-select v-model="dialog.form.title" placeholder="请选择职称" style="width: 100%">
                <el-option label="主任医师" value="主任医师" />
                <el-option label="副主任医师" value="副主任医师" />
                <el-option label="主治医师" value="主治医师" />
                <el-option label="住院医师" value="住院医师" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="所属科室" prop="departmentIds">
          <el-select
            v-model="dialog.form.departmentIds"
            multiple
            placeholder="请选择科室"
            style="width: 100%"
            collapse-tags
            collapse-tags-tooltip
          >
            <el-option
              v-for="dept in departmentOptions"
              :key="dept.id"
              :label="dept.name"
              :value="dept.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="擅长" prop="specialties">
          <el-select
            v-model="dialog.form.specialties"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="输入擅长领域并按回车"
            style="width: 100%"
            collapse-tags
            collapse-tags-tooltip
          >
            <el-option v-for="item in specialtyOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>

        <el-form-item label="简介" prop="description">
          <el-input
            v-model="dialog.form.description"
            type="textarea"
            :rows="4"
            placeholder="请输入医生个人简介..."
          />
        </el-form-item>

        <el-form-item label="头像URL" prop="avatar">
          <el-input v-model="dialog.form.avatar" placeholder="请输入头像URL">
            <template #prefix><el-icon><Picture /></el-icon></template>
          </el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialog.visible = false">取消</el-button>
          <el-button type="primary" @click="handleSubmit" :loading="dialog.loading">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getDoctorList, createDoctor, updateDoctor } from '@/api/doctor'
import { getDepartmentList } from '@/api/department'
import { getUserList } from '@/api/user'
import { ElMessage } from 'element-plus'
import { Plus, Search, Edit, Calendar, Picture } from '@element-plus/icons-vue'

const router = useRouter()
const loading = ref(false)
const doctorList = ref([])
const total = ref(0)
const departmentOptions = ref([])
const userOptions = ref([])
const specialtyOptions = ref(['高血压', '糖尿病', '感冒', '儿科', '外科', '中医', '心理咨询', '康复理疗'])

const searchForm = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  departmentId: ''
})

const dialog = reactive({
  visible: false,
  isEdit: false,
  loading: false,
  form: {
    id: null,
    userId: null,
    name: '',
    title: '',
    departmentIds: [],
    specialties: [],
    description: '',
    avatar: ''
  }
})

const formRef = ref(null)

const rules = {
  userId: [{ required: true, message: '请选择关联用户', trigger: 'change' }],
  name: [{ required: true, message: '请输入医生姓名', trigger: 'blur' }],
  title: [{ required: true, message: '请选择职称', trigger: 'change' }],
  departmentIds: [{ required: true, message: '请选择科室', trigger: 'change' }],
  specialties: [{ required: true, message: '请输入擅长领域', trigger: 'change' }]
}

const fetchDoctorList = async () => {
  loading.value = true
  try {
    const res = await getDoctorList(searchForm)
    doctorList.value = res.data.records || res.data.list || []
    total.value = res.data.total
  } catch (error) {
    console.error('Failed to fetch doctors:', error)
  } finally {
    loading.value = false
  }
}

const fetchDepartments = async () => {
  try {
    const res = await getDepartmentList()
    departmentOptions.value = res.data
  } catch (error) {
    console.error('Failed to fetch departments:', error)
  }
}

const fetchUsers = async () => {
  try {
    const res = await getUserList({ pageNum: 1, pageSize: 200, role: 'DOCTOR' })
    const users = res.data.records || res.data.list || []
    userOptions.value = users.filter((user) => Array.isArray(user.roles) ? user.roles.includes('DOCTOR') : true)
  } catch (error) {
    console.error('Failed to fetch users:', error)
  }
}

const handleSearch = () => {
  searchForm.pageNum = 1
  fetchDoctorList()
}

const resetSearch = () => {
  searchForm.keyword = ''
  searchForm.departmentId = ''
  handleSearch()
}

const handleSizeChange = (val) => {
  searchForm.pageSize = val
  fetchDoctorList()
}

const handleCurrentChange = (val) => {
  searchForm.pageNum = val
  fetchDoctorList()
}

const handleAdd = () => {
  dialog.isEdit = false
  fetchUsers()
  dialog.visible = true
}

const handleEdit = (row) => {
  dialog.isEdit = true
  const parseSpecialties = (val) => {
    if (Array.isArray(val)) return val
    if (typeof val === 'string') return val.split(/[,，]/).filter(Boolean)
    return []
  }
  
  dialog.form = {
    id: row.id,
    userId: row.userId || null,
    name: row.name,
    title: row.title,
    departmentIds: row.departments?.map(d => d.id) || [],
    specialties: parseSpecialties(row.specialties),
    description: row.description,
    avatar: row.avatar
  }
  dialog.visible = true
}

const resetDialog = () => {
  dialog.form = {
    id: null,
    userId: null,
    name: '',
    title: '',
    departmentIds: [],
    specialties: [],
    description: '',
    avatar: ''
  }
  if (formRef.value) formRef.value.resetFields()
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      dialog.loading = true
      try {
        const payload = {
          ...dialog.form,
          specialties: dialog.form.specialties.join(',')
        }
        if (dialog.isEdit) {
          await updateDoctor(dialog.form.id, payload)
          ElMessage.success('更新成功')
        } else {
          await createDoctor(payload)
          ElMessage.success('创建成功')
        }
        dialog.visible = false
        fetchDoctorList()
      } catch (error) {
        console.error('Failed to save doctor:', error)
      } finally {
        dialog.loading = false
      }
    }
  })
}

const goToSchedule = () => {
  router.push('/doctor/schedule')
}

onMounted(() => {
  fetchDoctorList()
  fetchDepartments()
  fetchUsers()
})
</script>

<style scoped>
.doctor-management {
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

.search-input {
  width: 200px;
}

.dept-select {
  width: 160px;
}

.right-panel {
  display: flex;
  align-items: center;
  gap: 12px;
}

.divider {
  width: 1px;
  height: 20px;
  background-color: var(--border-color);
  margin: 0 4px;
}

.doctor-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.doctor-name {
  font-weight: 500;
  color: var(--text-primary);
}

.dept-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.pagination-container {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}
</style>
