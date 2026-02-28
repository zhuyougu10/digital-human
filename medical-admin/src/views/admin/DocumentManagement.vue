<template>
  <div class="document-management">
    <div class="header-actions">
      <el-button @click="router.back()">返回知识库</el-button>
      <div>
        <el-button type="success" @click="addChunkDialog.visible = true">添加知识条目</el-button>
        <el-button type="primary" @click="uploadDialog.visible = true">上传文档</el-button>
      </div>
    </div>

    <el-card shadow="never" class="m-t-20">
      <el-table :data="docList" v-loading="loadingDocs" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="type" label="类型" width="100" />
        <el-table-column prop="size" label="大小" width="120">
          <template #default="{ row }">
            {{ formatSize(row.size) }}
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="分块数" width="100" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewChunks(row)">查看分块</el-button>
            <el-button type="danger" link @click="handleDeleteDoc(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="searchForm.pageNum"
          v-model:page-size="searchForm.pageSize"
          layout="total, prev, pager, next"
          :total="totalDocs"
          @current-change="fetchDocs"
        />
      </div>
    </el-card>

    <!-- Upload Dialog -->
    <el-dialog v-model="uploadDialog.visible" title="上传文档" width="500px">
      <el-upload
        class="upload-demo"
        drag
        action="#"
        :auto-upload="false"
        :on-change="handleFileChange"
        :file-list="uploadDialog.fileList"
        multiple
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">
          拖拽文件到此处或 <em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            支持 PDF, Word, TXT 格式，单个文件不超过 10MB
          </div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="uploadDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="handleUpload" :loading="uploadDialog.loading">
          开始上传
        </el-button>
      </template>
    </el-dialog>

    <!-- Add Manual Chunk Dialog -->
    <el-dialog v-model="addChunkDialog.visible" title="添加知识条目" width="600px">
      <el-form :model="addChunkDialog.form" :rules="addChunkDialog.rules" ref="chunkFormRef" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="addChunkDialog.form.title" placeholder="请输入标题" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="addChunkDialog.form.content"
            type="textarea"
            :rows="8"
            placeholder="请输入知识条目内容"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addChunkDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="handleAddChunk" :loading="addChunkDialog.loading">确定</el-button>
      </template>
    </el-dialog>

    <!-- Chunks Drawer -->
    <el-drawer
      v-model="chunkDrawer.visible"
      :title="`文档分块: ${chunkDrawer.currentDoc?.name}`"
      size="600px"
    >
      <div v-loading="loadingChunks">
        <div v-for="chunk in chunks" :key="chunk.id" class="chunk-item">
          <div class="chunk-header">
            <span class="chunk-id">Chunk #{{ chunk.id }}</span>
            <el-button type="danger" link @click="handleDeleteChunk(chunk)">删除</el-button>
          </div>
          <div class="chunk-content">{{ chunk.content }}</div>
        </div>
        <el-empty v-if="chunks.length === 0" description="暂无分块信息" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { 
  getDocumentList, 
  uploadDocument, 
  deleteDocument, 
  getChunkList, 
  addManualChunk, 
  deleteChunk 
} from '@/api/knowledge'
import { UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const kbId = route.params.kbId

const loadingDocs = ref(false)
const docList = ref([])
const totalDocs = ref(0)
const searchForm = reactive({
  pageNum: 1,
  pageSize: 10
})

const uploadDialog = reactive({
  visible: false,
  loading: false,
  fileList: []
})

const addChunkDialog = reactive({
  visible: false,
  loading: false,
  form: {
    title: '',
    content: ''
  },
  rules: {
    title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
    content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
  }
})
const chunkFormRef = ref(null)

const chunkDrawer = reactive({
  visible: false,
  currentDoc: null
})
const loadingChunks = ref(false)
const chunks = ref([])

const fetchDocs = async () => {
  loadingDocs.value = true
  try {
    const res = await getDocumentList(kbId, searchForm)
    docList.value = res.data.list
    totalDocs.value = res.data.total
  } catch (error) {
    console.error('Failed to fetch documents:', error)
  } finally {
    loadingDocs.value = false
  }
}

const handleFileChange = (file, fileList) => {
  uploadDialog.fileList = fileList
}

const handleUpload = async () => {
  if (uploadDialog.fileList.length === 0) {
    return ElMessage.warning('请先选择文件')
  }

  uploadDialog.loading = true
  try {
    for (const fileItem of uploadDialog.fileList) {
      await uploadDocument(kbId, fileItem.raw)
    }
    ElMessage.success('上传成功，后台正在解析')
    uploadDialog.visible = false
    uploadDialog.fileList = []
    fetchDocs()
  } catch (error) {
    console.error('Upload failed:', error)
  } finally {
    uploadDialog.loading = false
  }
}

const handleDeleteDoc = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除文档 "${row.name}" 吗？`, '提示', { type: 'warning' })
    await deleteDocument(row.id)
    ElMessage.success('删除成功')
    fetchDocs()
  } catch (error) {
    if (error !== 'cancel') console.error('Failed to delete doc:', error)
  }
}

const viewChunks = async (row) => {
  chunkDrawer.currentDoc = row
  chunkDrawer.visible = true
  loadingChunks.value = true
  try {
    const res = await getChunkList(row.id, { pageNum: 1, pageSize: 100 })
    chunks.value = res.data.list
  } catch (error) {
    console.error('Failed to fetch chunks:', error)
  } finally {
    loadingChunks.value = false
  }
}

const handleAddChunk = async () => {
  if (!chunkFormRef.value) return
  await chunkFormRef.value.validate(async (valid) => {
    if (valid) {
      addChunkDialog.loading = true
      try {
        await addManualChunk(kbId, addChunkDialog.form)
        ElMessage.success('添加成功')
        addChunkDialog.visible = false
        addChunkDialog.form = { title: '', content: '' }
        fetchDocs()
      } catch (error) {
        console.error('Failed to add chunk:', error)
      } finally {
        addChunkDialog.loading = false
      }
    }
  })
}

const handleDeleteChunk = async (chunk) => {
  try {
    await ElMessageBox.confirm('确定要删除这个分块吗？', '提示', { type: 'warning' })
    await deleteChunk(chunk.id)
    ElMessage.success('删除成功')
    const index = chunks.value.findIndex(c => c.id === chunk.id)
    chunks.value.splice(index, 1)
  } catch (error) {
    if (error !== 'cancel') console.error('Failed to delete chunk:', error)
  }
}

const formatSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const getStatusType = (status) => {
  const map = {
    'SUCCESS': 'success',
    'PARSING': 'warning',
    'FAILED': 'danger',
    'PENDING': 'info'
  }
  return map[status] || 'info'
}

onMounted(() => {
  fetchDocs()
})
</script>

<style scoped>
.document-management {
  padding: 20px;
}
.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.m-t-20 {
  margin-top: 20px;
}
.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
.chunk-item {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 15px;
  margin-bottom: 15px;
}
.chunk-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  color: #909399;
  font-size: 12px;
}
.chunk-content {
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  color: #303133;
}
</style>
