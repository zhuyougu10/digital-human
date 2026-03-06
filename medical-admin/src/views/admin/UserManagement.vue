<template>
  <div class="user-management">
    <el-card shadow="hover" class="main-card">
      <template #header>
        <div class="card-header">
          <div class="left-panel">
            <span class="title">用户管理</span>
          </div>
          <div class="right-panel">
            <el-form :inline="true" :model="searchForm" class="search-form">
              <el-form-item>
                <el-input 
                  v-model="searchForm.keyword" 
                  placeholder="搜索用户名/昵称/手机号" 
                  clearable 
                  @keyup.enter="handleSearch"
                  class="search-input"
                >
                  <template #prefix><el-icon><Search /></el-icon></template>
                </el-input>
              </el-form-item>
              <el-form-item>
                <el-select v-model="searchForm.role" placeholder="全部角色" clearable class="role-select">
                  <el-option label="管理员" value="ADMIN" />
                  <el-option label="医生" value="DOCTOR" />
                  <el-option label="普通用户" value="USER" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleSearch">查询</el-button>
                <el-button @click="resetSearch">重置</el-button>
              </el-form-item>
            </el-form>
            <el-button type="primary" @click="openCreateDialog">
              <el-icon class="mr-1"><Plus /></el-icon>新增用户
            </el-button>
          </div>
        </div>
      </template>

      <el-table 
        :data="userList" 
        v-loading="loading" 
        style="width: 100%"
        :header-cell-style="{ background: '#F7F8FA', color: '#1F2937', fontWeight: '600' }"
      >
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="phone" label="手机号" width="120" />
        <el-table-column label="角色" min-width="150">
          <template #default="{ row }">
            <div class="role-tags">
              <el-tag 
                v-for="role in row.roles" 
                :key="role" 
                :type="getRoleTagType(role)"
                size="small"
                effect="plain"
              >
                {{ getRoleLabel(role) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
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
        <el-table-column prop="createTime" label="注册时间" width="180" />
        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openRoleDialog(row)">
              <el-icon class="mr-1"><Edit /></el-icon>分配角色
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="searchForm.page"
          v-model:page-size="searchForm.size"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          background
        />
      </div>
    </el-card>

    <!-- Create User Dialog -->
    <el-dialog 
      v-model="createDialog.visible" 
      title="新增用户" 
      width="500px"
      class="custom-dialog"
      destroy-on-close
    >
      <el-form :model="createDialog.form" :rules="createDialog.rules" ref="createFormRef" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createDialog.form.username" placeholder="请输入用户名 (3-30位)" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="createDialog.form.password" type="password" show-password placeholder="请输入密码 (6-30位)" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="createDialog.form.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="createDialog.form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="createDialog.form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="createDialog.form.gender">
            <el-radio :label="1">男</el-radio>
            <el-radio :label="2">女</el-radio>
            <el-radio :label="0">未知</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="角色" prop="roleKey">
          <el-select v-model="createDialog.form.roleKey" placeholder="请选择角色">
            <el-option label="普通用户" value="USER" />
            <el-option label="医生" value="DOCTOR" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch v-model="createDialog.form.status" :active-value="0" :inactive-value="1" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="createDialog.visible = false">取消</el-button>
          <el-button type="primary" @click="submitCreateUser" :loading="createDialog.loading">确定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Role Assignment Dialog -->
    <el-dialog 
      v-model="roleDialog.visible" 
      title="分配角色" 
      width="400px"
      class="custom-dialog"
      destroy-on-close
    >
      <el-form :model="roleDialog.form" label-width="80px" class="role-form">
        <el-form-item label="用户名">
          <span class="user-label">{{ roleDialog.currentUser.username }}</span>
        </el-form-item>
        <el-form-item label="角色">
          <el-checkbox-group v-model="roleDialog.form.roles" class="role-checkbox-group">
            <el-checkbox label="ADMIN" border>管理员</el-checkbox>
            <el-checkbox label="DOCTOR" border>医生</el-checkbox>
            <el-checkbox label="USER" border>普通用户</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="roleDialog.visible = false">取消</el-button>
          <el-button type="primary" @click="handleAssignRole" :loading="roleDialog.loading">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getUserList, toggleUserStatus, assignRole, removeRole, createUser } from '@/api/user'
import { createDoctor } from '@/api/doctor'
import { ElMessage } from 'element-plus'
import { Search, Edit, Plus } from '@element-plus/icons-vue'

const loading = ref(false)
const userList = ref([])
const total = ref(0)
const createFormRef = ref(null)

const searchForm = reactive({
  page: 1,
  size: 10,
  keyword: '',
  role: ''
})

const createDialog = reactive({
  visible: false,
  loading: false,
  form: {
    username: '',
    password: '',
    nickname: '',
    phone: '',
    email: '',
    gender: 0,
    roleKey: 'USER',
    status: 0
  },
  rules: {
    username: [
      { required: true, message: '请输入用户名', trigger: 'blur' },
      { min: 3, max: 30, message: '长度在 3 到 30 个字符', trigger: 'blur' }
    ],
    password: [
      { required: true, message: '请输入密码', trigger: 'blur' },
      { min: 6, max: 30, message: '长度在 6 到 30 个字符', trigger: 'blur' }
    ],
    roleKey: [
      { required: true, message: '请选择角色', trigger: 'change' }
    ]
  }
})

const roleDialog = reactive({
  visible: false,
  loading: false,
  currentUser: {},
  form: {
    roles: []
  }
})

const fetchUserList = async () => {
  loading.value = true
  try {
    const res = await getUserList(searchForm)
    userList.value = res.data.records || res.data.list || []
    total.value = res.data.total
  } catch (error) {
    console.error('Failed to fetch users:', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  searchForm.page = 1
  fetchUserList()
}

const resetSearch = () => {
  searchForm.keyword = ''
  searchForm.role = ''
  handleSearch()
}

const handleSizeChange = (val) => {
  searchForm.size = val
  fetchUserList()
}

const handleCurrentChange = (val) => {
  searchForm.page = val
  fetchUserList()
}

const handleStatusChange = async (row, val) => {
  try {
    await toggleUserStatus(row.id)
    ElMessage.success('状态更新成功')
  } catch (error) {
    row.status = val === 1 ? 0 : 1 // Revert switch if failed
    console.error('Failed to update status:', error)
  }
}

const openCreateDialog = () => {
  createDialog.visible = true
  // Reset form
  createDialog.form = {
    username: '',
    password: '',
    nickname: '',
    phone: '',
    email: '',
    gender: 0,
    roleKey: 'USER',
    status: 0
  }
  if (createFormRef.value) {
    createFormRef.value.clearValidate()
  }
}

const submitCreateUser = async () => {
  if (!createFormRef.value) return
  await createFormRef.value.validate(async (valid) => {
    if (valid) {
      createDialog.loading = true
      try {
        const res = await createUser(createDialog.form)
        const createdUser = res?.data
        if (createDialog.form.roleKey === 'DOCTOR' && createdUser?.id) {
          await createDoctor({
            userId: createdUser.id,
            name: createDialog.form.nickname || createDialog.form.username,
            title: '住院医师',
            consultationFee: 0,
            departmentIds: []
          })
        }
        ElMessage.success('用户创建成功')
        createDialog.visible = false
        fetchUserList()
      } catch (error) {
        console.error('Failed to create user:', error)
      } finally {
        createDialog.loading = false
      }
    }
  })
}

const openRoleDialog = (row) => {
  roleDialog.currentUser = row
  roleDialog.form.roles = row.roles ? [...row.roles] : []
  roleDialog.visible = true
}

const handleAssignRole = async () => {
  roleDialog.loading = true
  try {
    const originalRoles = new Set(roleDialog.currentUser.roles || [])
    const selectedRoles = new Set(roleDialog.form.roles || [])
    const rolesToAdd = [...selectedRoles].filter(role => !originalRoles.has(role))
    const rolesToRemove = [...originalRoles].filter(role => !selectedRoles.has(role))

    for (const roleKey of rolesToAdd) {
      await assignRole(roleDialog.currentUser.id, roleKey)
    }
    for (const roleKey of rolesToRemove) {
      await removeRole(roleDialog.currentUser.id, roleKey)
    }
    ElMessage.success('角色分配成功')
    roleDialog.visible = false
    fetchUserList()
  } catch (error) {
    console.error('Failed to assign role:', error)
  } finally {
    roleDialog.loading = false
  }
}

const getRoleLabel = (role) => {
  const map = {
    'ADMIN': '管理员',
    'DOCTOR': '医生',
    'USER': '普通用户'
  }
  return map[role] || role
}

const getRoleTagType = (role) => {
  const map = {
    'ADMIN': 'danger',
    'DOCTOR': 'success',
    'USER': 'info'
  }
  return map[role] || 'info'
}

onMounted(() => {
  fetchUserList()
})
</script>

<style scoped>
.user-management {
  /* No padding needed if parent container has padding, keeping minimal structure */
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

.right-panel {
  display: flex;
  align-items: center;
  gap: 12px;
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

.role-select {
  width: 120px;
}

.role-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.pagination-container {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}

.user-label {
  font-weight: 500;
  color: var(--text-primary);
}

.role-checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.role-checkbox-group .el-checkbox {
  margin-left: 0;
}

/* Custom dialog tweaks if needed, mostly handled by global theme */
</style>
