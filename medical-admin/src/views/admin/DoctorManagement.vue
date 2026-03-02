<template>
  <div class="doctor-management">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="姓名/职称" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="科室">
          <el-select v-model="searchForm.departmentId" placeholder="选择科室" clearable style="width: 200px">
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
          <el-button type="success" @click="handleAdd">新增医生</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="m-t-20">
      <el-table :data="doctorList" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="姓名" width="120">
          <template #default="{ row }">
            <div class="doctor-info">
              <el-avatar :size="32" :src="row.avatar">{{ row.name?.charAt(0) }}</el-avatar>
              <span class="m-l-10">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="职称" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ row.title }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="所属科室" min-width="150">
          <template #default="{ row }">
            <el-tag v-for="dept in row.departments" :key="dept.id" size="small" class="m-r-5">
              {{ dept.name }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="specialties" label="擅长" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="primary" link @click="goToSchedule(row)">排班</el-button>
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

    <!-- Add/Edit Dialog -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.isEdit ? '编辑医生' : '新增医生'"
      width="700px"
      @closed="resetDialog"
    >
      <el-form :model="dialog.form" :rules="rules" ref="formRef" label-width="100px">
        <el-row :gutter="20">
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
          >
            <el-option v-for="item in specialtyOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>

        <el-form-item label="简介" prop="description">
          <el-input
            v-model="dialog.form.description"
            type="textarea"
            :rows="4"
            placeholder="请输入医生简介"
          />
        </el-form-item>

        <el-form-item label="头像URL" prop="avatar">
          <el-input v-model="dialog.form.avatar" placeholder="请输入头像URL" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="dialog.loading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getDoctorList, createDoctor, updateDoctor } from '@/api/doctor'
import { getDepartmentList } from '@/api/department'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const doctorList = ref([])
const total = ref(0)
const departmentOptions = ref([])
// 常用擅长领域标签（快捷选择用，用户也可手动输入自定义标签）
const specialtyOptions = ref(['高血压', '糖尿病', '感冒', '儿科', '外科', '中医'])

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
  name: [{ required: true, message: '请输入医生姓名', trigger: 'blur' }],
  title: [{ required: true, message: '请选择职称', trigger: 'change' }],
  departmentIds: [{ required: true, message: '请选择科室', trigger: 'change' }],
  specialties: [{ required: true, message: '请输入擅长领域', trigger: 'change' }]
}

const fetchDoctorList = async () => {
  loading.value = true
  try {
    const res = await getDoctorList(searchForm)
    doctorList.value = res.data.list
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
  dialog.visible = true
}

const handleEdit = (row) => {
  dialog.isEdit = true
  dialog.form = {
    id: row.id,
    name: row.name,
    title: row.title,
    departmentIds: row.departments?.map(d => d.id) || [],
    specialties: row.specialties ? row.specialties.split(',') : [],
    description: row.description,
    avatar: row.avatar
  }
  dialog.visible = true
}

const resetDialog = () => {
  dialog.form = {
    id: null,
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
})
</script>

<style scoped>
.doctor-management {
  padding: 20px;
}
.search-card {
  margin-bottom: 20px;
}
.m-t-20 {
  margin-top: 20px;
}
.m-l-10 {
  margin-left: 10px;
}
.m-r-5 {
  margin-right: 5px;
}
.doctor-info {
  display: flex;
  align-items: center;
}
.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
