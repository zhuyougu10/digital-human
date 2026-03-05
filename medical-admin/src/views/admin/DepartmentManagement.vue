<template>
  <div class="department-management">
    <el-card shadow="hover" class="main-card">
      <template #header>
        <div class="card-header">
          <div class="left-panel">
            <span class="title">科室管理</span>
          </div>
          <div class="right-panel">
            <el-form :inline="true" :model="searchForm" class="search-form">
              <el-form-item>
                <el-input 
                  v-model="searchForm.keyword" 
                  placeholder="搜索科室名称" 
                  clearable 
                  @keyup.enter="handleSearch"
                  class="search-input"
                >
                  <template #prefix><el-icon><Search /></el-icon></template>
                </el-input>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleSearch">查询</el-button>
                <el-button @click="resetSearch">重置</el-button>
              </el-form-item>
            </el-form>
            <div class="divider"></div>
            <el-button type="primary" icon="Plus" @click="openDialog()">新增科室</el-button>
          </div>
        </div>
      </template>

      <el-table 
        :data="departmentList" 
        v-loading="loading" 
        style="width: 100%"
        :header-cell-style="{ background: '#F7F8FA', color: '#1F2937', fontWeight: '600' }"
      >
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="name" label="科室名称" min-width="150" />
        <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              inline-prompt
              active-text="启用"
              inactive-text="停用"
              @change="(val) => handleStatusChange(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDialog(row)">
              <el-icon class="mr-1"><Edit /></el-icon>编辑
            </el-button>
            <el-popconfirm title="确定删除该科室吗？" @confirm="handleDelete(row)" width="200px">
              <template #reference>
                <el-button type="danger" link>
                  <el-icon class="mr-1"><Delete /></el-icon>删除
                </el-button>
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
      class="custom-dialog"
      destroy-on-close
    >
      <el-form :model="dialog.form" :rules="rules" ref="formRef" label-width="80px" class="department-form">
        <el-form-item label="科室名称" prop="name">
          <el-input v-model="dialog.form.name" placeholder="请输入科室名称" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="dialog.form.sortOrder" :min="0" controls-position="right" />
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
import { 
  getDepartmentList, 
  createDepartment, 
  updateDepartment, 
  toggleDepartmentStatus,
  deleteDepartment
} from '@/api/department'
import { ElMessage } from 'element-plus'
import { Plus, Search, Edit, Delete } from '@element-plus/icons-vue'

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
  width: 220px;
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

/* Custom dialog tweaks */
</style>
