<template>
  <div class="user-management">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="用户名/昵称/手机号" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="searchForm.role" placeholder="选择角色" clearable style="width: 150px">
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
    </el-card>

    <el-card shadow="never" class="m-t-20">
      <el-table :data="userList" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="phone" label="手机号" />
        <el-table-column label="角色" min-width="120">
          <template #default="{ row }">
            <el-tag v-for="role in row.roles" :key="role" class="m-r-5" size="small">
              {{ role }}
            </el-tag>
          </template>
        </el-table-column>
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
        <el-table-column prop="createTime" label="注册时间" width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openRoleDialog(row)">分配角色</el-button>
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
        />
      </div>
    </el-card>

    <!-- Role Assignment Dialog -->
    <el-dialog v-model="roleDialog.visible" title="分配角色" width="400px">
      <el-form :model="roleDialog.form" label-width="80px">
        <el-form-item label="用户名">
          <span>{{ roleDialog.currentUser.username }}</span>
        </el-form-item>
        <el-form-item label="角色">
          <el-checkbox-group v-model="roleDialog.form.roles">
            <el-checkbox label="ADMIN">管理员</el-checkbox>
            <el-checkbox label="DOCTOR">医生</el-checkbox>
            <el-checkbox label="USER">普通用户</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="handleAssignRole" :loading="roleDialog.loading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getUserList, toggleUserStatus, assignRole } from '@/api/user'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const userList = ref([])
const total = ref(0)

const searchForm = reactive({
  page: 1,
  size: 10,
  keyword: '',
  role: ''
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
    userList.value = res.data.list
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

const openRoleDialog = (row) => {
  roleDialog.currentUser = row
  roleDialog.form.roles = [...row.roles]
  roleDialog.visible = true
}

const handleAssignRole = async () => {
  roleDialog.loading = true
  try {
    // Loop through roles to call single role assignment API
    for (const roleKey of roleDialog.form.roles) {
      await assignRole(roleDialog.currentUser.id, roleKey)
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

onMounted(() => {
  fetchUserList()
})
</script>

<style scoped>
.user-management {
  padding: 20px;
}
.search-card {
  margin-bottom: 20px;
}
.m-t-20 {
  margin-top: 20px;
}
.m-r-5 {
  margin-right: 5px;
}
.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
