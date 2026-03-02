<template>
  <div class="knowledge-base">
    <div class="header-actions">
      <h2>知识库管理</h2>
      <el-button type="primary" @click="handleAdd">创建知识库</el-button>
    </div>

    <el-row :gutter="20" v-loading="loading">
      <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="kb in kbList" :key="kb.id" class="m-b-20">
        <el-card shadow="hover" class="kb-card">
          <template #header>
            <div class="kb-card-header">
              <span class="kb-name">{{ kb.name }}</span>
              <el-dropdown trigger="click" @command="(cmd) => handleCommand(cmd, kb)">
                <span class="el-dropdown-link">
                  <el-icon><MoreFilled /></el-icon>
                </span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="delete" style="color: #f56c6c">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
          <div class="kb-card-content" @click="goToDocuments(kb)">
            <p class="kb-description">{{ kb.description || '暂无描述' }}</p>
            <div class="kb-stats">
              <div class="stat-item">
                <span class="stat-label">文档数</span>
                <span class="stat-value">{{ kb.documentCount || 0 }}</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">分块数</span>
                <span class="stat-value">{{ kb.chunkCount || 0 }}</span>
              </div>
            </div>
          </div>
          <div class="kb-card-footer">
            <el-button type="primary" link @click="goToDocuments(kb)">进入管理</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="!loading && kbList.length === 0" description="暂无知识库" />

    <!-- Create KB Dialog -->
    <el-dialog v-model="dialog.visible" title="创建知识库" width="500px">
      <el-form :model="dialog.form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="dialog.form.name" placeholder="请输入知识库名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="dialog.form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入知识库描述"
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
import { useRouter } from 'vue-router'
import { getKnowledgeBaseList, createKnowledgeBase, deleteKnowledgeBase } from '@/api/knowledge'
import { MoreFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const kbList = ref([])

const dialog = reactive({
  visible: false,
  loading: false,
  form: {
    name: '',
    description: ''
  }
})

const formRef = ref(null)
const rules = {
  name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }]
}

const fetchKBList = async () => {
  loading.value = true
  try {
    const res = await getKnowledgeBaseList({ pageNum: 1, pageSize: 100 })
    kbList.value = res.data.records
  } catch (error) {
    console.error('Failed to fetch knowledge bases:', error)
  } finally {
    loading.value = false
  }
}

const handleAdd = () => {
  dialog.form = { name: '', description: '' }
  dialog.visible = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      dialog.loading = true
      try {
        await createKnowledgeBase(dialog.form)
        ElMessage.success('创建成功')
        dialog.visible = false
        fetchKBList()
      } catch (error) {
        console.error('Failed to create KB:', error)
      } finally {
        dialog.loading = false
      }
    }
  })
}

const handleCommand = (cmd, kb) => {
  if (cmd === 'delete') {
    handleDelete(kb)
  }
}

const handleDelete = async (kb) => {
  try {
    await ElMessageBox.confirm(`确定要删除知识库 "${kb.name}" 吗？此操作不可恢复。`, '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteKnowledgeBase(kb.id)
    ElMessage.success('删除成功')
    fetchKBList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('Failed to delete KB:', error)
    }
  }
}

const goToDocuments = (kb) => {
  router.push(`/admin/knowledge/${kb.id}/documents`)
}

onMounted(() => {
  fetchKBList()
})
</script>

<style scoped>
.knowledge-base {
  padding: 20px;
}
.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.kb-card {
  height: 220px;
  display: flex;
  flex-direction: column;
}
.kb-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.kb-name {
  font-weight: bold;
  font-size: 16px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kb-card-content {
  flex: 1;
  cursor: pointer;
}
.kb-description {
  font-size: 14px;
  color: #606266;
  height: 40px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  margin-bottom: 15px;
}
.kb-stats {
  display: flex;
  gap: 20px;
}
.stat-item {
  display: flex;
  flex-direction: column;
}
.stat-label {
  font-size: 12px;
  color: #909399;
}
.stat-value {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
}
.kb-card-footer {
  margin-top: 15px;
  text-align: right;
}
.m-b-20 {
  margin-bottom: 20px;
}
.el-dropdown-link {
  cursor: pointer;
  color: #909399;
}
</style>
