<template>
  <div class="knowledge-base">
    <div class="page-header">
      <div class="left-panel">
        <h2 class="title">知识库管理</h2>
        <p class="subtitle">管理医疗知识库、文档和向量索引</p>
      </div>
      <el-button type="primary" icon="Plus" @click="handleAdd">创建知识库</el-button>
    </div>

    <div v-loading="loading" class="kb-list">
      <el-row :gutter="24">
        <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="kb in kbList" :key="kb.id" class="mb-6">
          <el-card shadow="hover" class="kb-card" :body-style="{ padding: '0' }">
            <div class="kb-card-header">
              <div class="kb-icon" :style="{ backgroundColor: getRandomColor(kb.id) }">
                <el-icon><Reading /></el-icon>
              </div>
              <div class="kb-actions">
                <el-dropdown trigger="click" @command="(cmd) => handleCommand(cmd, kb)">
                  <span class="el-dropdown-link" @click.stop>
                    <el-icon><MoreFilled /></el-icon>
                  </span>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="edit">编辑</el-dropdown-item>
                      <el-dropdown-item command="delete" style="color: #FF4D4F">删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>
            
            <div class="kb-card-body" @click="goToDocuments(kb)">
              <h3 class="kb-name" :title="kb.name">{{ kb.name }}</h3>
              <p class="kb-desc" :title="kb.description">{{ kb.description || '暂无描述' }}</p>
              
              <div class="kb-stats">
                <div class="stat-item">
                  <span class="value">{{ kb.documentCount || 0 }}</span>
                  <span class="label">文档</span>
                </div>
                <div class="stat-divider"></div>
                <div class="stat-item">
                  <span class="value">{{ kb.chunkCount || 0 }}</span>
                  <span class="label">分块</span>
                </div>
              </div>
            </div>
            
            <div class="kb-card-footer">
              <span class="time">{{ formatDate(kb.createTime) }}</span>
              <el-button type="primary" link @click="goToDocuments(kb)">
                进入管理 <el-icon class="ml-1"><ArrowRight /></el-icon>
              </el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
      
      <el-empty v-if="!loading && kbList.length === 0" description="暂无知识库，请创建" />
    </div>

    <!-- Create/Edit KB Dialog -->
    <el-dialog 
      v-model="dialog.visible" 
      :title="dialog.isEdit ? '编辑知识库' : '创建知识库'" 
      width="500px"
      class="custom-dialog"
      destroy-on-close
    >
      <el-form :model="dialog.form" :rules="rules" ref="formRef" label-width="80px" label-position="top">
        <el-form-item label="名称" prop="name">
          <el-input v-model="dialog.form.name" placeholder="请输入知识库名称 (如: 心内科知识库)" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="dialog.form.description"
            type="textarea"
            :rows="4"
            placeholder="请输入知识库描述，用于辅助检索..."
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
import { useRouter } from 'vue-router'
import { getKnowledgeBaseList, createKnowledgeBase, deleteKnowledgeBase } from '@/api/knowledge'
import { MoreFilled, Reading, Plus, ArrowRight } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'

const router = useRouter()
const loading = ref(false)
const kbList = ref([])

const dialog = reactive({
  visible: false,
  loading: false,
  isEdit: false,
  form: {
    id: null,
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
  dialog.isEdit = false
  dialog.form = { name: '', description: '' }
  dialog.visible = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      dialog.loading = true
      try {
        // Currently API only supports create, update logic can be added if backend supports it
        // For now treating as create for simplicity based on original file
        await createKnowledgeBase(dialog.form)
        ElMessage.success('操作成功')
        dialog.visible = false
        fetchKBList()
      } catch (error) {
        console.error('Failed to save KB:', error)
      } finally {
        dialog.loading = false
      }
    }
  })
}

const handleCommand = (cmd, kb) => {
  if (cmd === 'delete') {
    handleDelete(kb)
  } else if (cmd === 'edit') {
    // Mock edit for now or if API exists
    dialog.isEdit = true
    dialog.form = { ...kb }
    dialog.visible = true
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

const getRandomColor = (id) => {
  const colors = ['#1677FF', '#52C41A', '#FAAD14', '#722ED1', '#13C2C2', '#F5222D']
  return colors[id % colors.length]
}

const formatDate = (date) => {
  return dayjs(date).format('YYYY-MM-DD')
}

onMounted(() => {
  fetchKBList()
})
</script>

<style scoped>
.knowledge-base {
  /* No padding needed if parent has it */
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.title {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 4px 0;
}

.subtitle {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
}

.kb-card {
  height: 100%;
  border: none;
  border-radius: var(--radius-md);
  transition: all 0.3s;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--border-color-light);
}

.kb-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-md);
  border-color: var(--primary-color-light-5);
}

.kb-card-header {
  padding: 20px 20px 0;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.kb-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 24px;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.1);
}

.kb-actions {
  color: var(--text-secondary);
  cursor: pointer;
}

.el-dropdown-link:hover {
  color: var(--primary-color);
}

.kb-card-body {
  padding: 20px;
  flex: 1;
  cursor: pointer;
}

.kb-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kb-desc {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0 0 24px;
  height: 40px;
  line-height: 20px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.kb-stats {
  display: flex;
  align-items: center;
  background-color: var(--bg-page);
  border-radius: 8px;
  padding: 12px;
}

.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-divider {
  width: 1px;
  height: 24px;
  background-color: var(--border-color);
}

.stat-item .value {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.2;
}

.stat-item .label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.kb-card-footer {
  padding: 12px 20px;
  border-top: 1px solid var(--border-color-light);
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: #FAFAFA;
}

.time {
  font-size: 12px;
  color: var(--text-placeholder);
}

.mb-6 {
  margin-bottom: 24px;
}

.ml-1 {
  margin-left: 4px;
}
</style>
