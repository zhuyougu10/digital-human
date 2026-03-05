<template>
  <div class="system-config">
    <el-card shadow="hover" class="main-card">
      <template #header>
        <div class="card-header">
          <span class="title">系统设置</span>
          <el-button type="primary" :loading="saving" @click="saveConfig">保存配置</el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab" class="config-tabs">
        <el-tab-pane label="AI 参数设置" name="ai">
          <div class="tab-content">
            <el-alert
              title="注意：以下配置仅保存在本地浏览器缓存中，清除缓存后会重置"
              type="warning"
              show-icon
              :closable="false"
              class="mb-4"
            />
            
            <el-form :model="aiConfig" label-width="120px" class="config-form">
              <el-form-item label="模型名称">
                <el-select v-model="aiConfig.modelName" style="width: 100%">
                  <el-option label="DeepSeek R1" value="deepseek-r1" />
                  <el-option label="DeepSeek V3" value="deepseek-v3" />
                  <el-option label="GPT-4o" value="gpt-4o" />
                  <el-option label="Claude 3.5 Sonnet" value="claude-3-5-sonnet" />
                </el-select>
              </el-form-item>
              
              <el-form-item label="Temperature">
                <div class="slider-container">
                  <el-slider v-model="aiConfig.temperature" :min="0" :max="1" :step="0.1" show-input />
                  <span class="help-text">值越大，回答越随机；值越小，回答越严谨</span>
                </div>
              </el-form-item>
              
              <el-form-item label="Max Tokens">
                <el-input-number v-model="aiConfig.maxTokens" :min="100" :max="8000" :step="100" />
              </el-form-item>

              <el-form-item label="提示词模板">
                <el-input 
                  v-model="aiConfig.promptTemplate" 
                  type="textarea" 
                  :rows="6"
                  placeholder="设置系统预设提示词..."
                />
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>
        
        <el-tab-pane label="基础设置" name="basic">
          <div class="tab-content">
             <el-empty description="基础设置功能开发中..." />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const activeTab = ref('ai')
const saving = ref(false)

const aiConfig = reactive({
  modelName: 'deepseek-r1',
  temperature: 0.7,
  maxTokens: 2000,
  promptTemplate: '你是一个专业的医疗助手，请根据知识库内容回答用户问题。如果不知道答案，请直接说明。'
})

const loadConfig = () => {
  const saved = localStorage.getItem('ai_config')
  if (saved) {
    Object.assign(aiConfig, JSON.parse(saved))
  }
}

const saveConfig = () => {
  saving.value = true
  // Mock saving delay
  setTimeout(() => {
    localStorage.setItem('ai_config', JSON.stringify(aiConfig))
    ElMessage.success('配置已保存')
    saving.value = false
  }, 500)
}

onMounted(() => {
  loadConfig()
})
</script>

<style scoped>
.system-config {
}

.main-card {
  border: none;
  border-radius: var(--radius-md);
  min-height: 600px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.tab-content {
  padding: 20px 0;
  max-width: 800px;
}

.mb-4 {
  margin-bottom: 24px;
}

.slider-container {
  width: 100%;
  display: flex;
  flex-direction: column;
}

.help-text {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}
</style>
