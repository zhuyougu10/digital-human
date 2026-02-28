<template>
  <div class="department-management">
    <el-card shadow="never" class="search-card">
      <div class="header-actions">
        <el-form :inline="true" :model="searchForm">
          <el-form-item label="科室名称">
            <el-input v-model="searchForm.keyword" placeholder="关键词搜索" clearable @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSearch">查询</el-button>
            <el-button @click="resetSearch">重置</el-button>
          </el-form-item>
        </el-form>
        <el-button type="primary" icon="Plus" @click="openDialog()">新增科室</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="m-t-20">
      <el-table :data="departmentList" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="科室名称" min-width="120" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              @change="(val) => handleStatusChange(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确定删除该科室吗？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button type="danger" link>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Add/Edit Dialog -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.isEdit ? '编辑科室' : '新增科室'"
      width="500px"
    >
      <el-form :model="dialog.form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="科室名称" prop="name">
          <el-input v-model="dialog.form.name" placeholder="请输入科室名称" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="dialog.form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="图标" prop="icon">
          <el-input v-model="dialog.form.icon" placeholder="图标类名" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="dialog.form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入科室描述"
          />
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
import { 
  getDepartmentList, 
  createDepartment, 
  updateDepartment, 
  toggleDepartmentStatus,
  deleteDepartment
} from '@/api/department'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

const loading = ref(false)
const departmentList = ref([])
const formRef = ref(null)

const searchForm = reactive({
  keyword: ''
})

const dialog = reactive({
  visible: false,
  isEdit: false,
  loading: false,
  form: {
    id: null,
    name: '',
    description: '',
    sortOrder: 0,
    icon: ''
  }
})

const rules = {
  name: [{ required: true, message: '请输入科室名称', trigger: 'blur' }]
}

const fetchDepartmentList = async () => {
  loading.value = true
  try {
    const res = await getDepartmentList(searchForm)
    departmentList.value = res.data
  } catch (error) {
    console.error('Failed to fetch departments:', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  fetchDepartmentList()
}

const resetSearch = () => {
  searchForm.keyword = ''
  handleSearch()
}

const openDialog = (row = null) => {
  dialog.isEdit = !!row
  if (row) {
    dialog.form = { ...row }
  } else {
    dialog.form = {
      id: null,
      name: '',
      description: '',
      sortOrder: 0,
      icon: ''
    }
  }
  dialog.visible = true
  if (formRef.value) formRef.value.clearValidate()
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  
  dialog.loading = true
  try {
    if (dialog.isEdit) {
      await updateDepartment(dialog.form.id, dialog.form)
      ElMessage.success('更新成功')
    } else {
      await createDepartment(dialog.form)
      ElMessage.success('创建成功')
    }
    dialog.visible = false
    fetchDepartmentList()
  } catch (error) {
    console.error('Failed to save department:', error)
  } finally {
    dialog.loading = false
  }
}

const handleStatusChange = async (row, val) => {
  try {
    await toggleDepartmentStatus(row.id, val)
    ElMessage.success('状态更新成功')
  } catch (error) {
    row.status = val === 1 ? 0 : 1
    console.error('Failed to update status:', error)
  }
}

const handleDelete = async (row) => {
  try {
    await deleteDepartment(row.id)
    ElMessage.success('删除成功')
    fetchDepartmentList()
  } catch (error) {
    console.error('Failed to delete department:', error)
  }
}

onMounted(() => {
  fetchDepartmentList()
})
</script>

<style scoped>
.department-management {
  padding: 20px;
}
.search-card {
  margin-bottom: 20px;
}
.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.m-t-20 {
  margin-top: 20px;
}
</style>
