<template>
  <div class="system-config">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>系统配置</span>
          <el-button type="primary" @click="handleSave">保存配置</el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- Agent Config -->
        <el-tab-pane label="Agent配置" name="agent">
          <el-form :model="config.agent" label-position="top">
            <el-form-item label="导诊 Agent System Prompt">
              <el-input
                v-model="config.agent.triagePrompt"
                type="textarea"
                :rows="4"
                placeholder="请输入提示词..."
              />
            </el-form-item>
            <el-form-item label="问答 Agent System Prompt">
              <el-input
                v-model="config.agent.qaPrompt"
                type="textarea"
                :rows="4"
                placeholder="请输入提示词..."
              />
            </el-form-item>
            <el-form-item label="摘要 Agent System Prompt">
              <el-input
                v-model="config.agent.summaryPrompt"
                type="textarea"
                :rows="4"
                placeholder="请输入提示词..."
              />
            </el-form-item>
            <el-form-item label="百科 Agent System Prompt">
              <el-input
                v-model="config.agent.encyclopediaPrompt"
                type="textarea"
                :rows="4"
                placeholder="请输入提示词..."
              />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- Model Params -->
        <el-tab-pane label="模型参数" name="model">
          <el-form :model="config.model" label-width="120px" style="max-width: 600px">
            <el-form-item label="Temperature">
              <el-slider v-model="config.model.temperature" :min="0" :max="2" :step="0.1" show-input />
              <div class="form-tip">较高的值会让模型输出更具随机性，较低的值则更具确定性。</div>
            </el-form-item>
            <el-form-item label="Max Tokens">
              <el-input-number v-model="config.model.maxTokens" :min="100" :max="4096" :step="100" />
            </el-form-item>
            <el-form-item label="Top P">
              <el-slider v-model="config.model.topP" :min="0" :max="1" :step="0.05" />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- TTS Config -->
        <el-tab-pane label="TTS配置" name="tts">
          <el-form :model="config.tts" label-width="100px" style="max-width: 600px">
            <el-form-item label="默认音色">
              <el-select v-model="config.tts.voice" placeholder="选择音色" style="width: 100%">
                <el-option label="柔美女性 (zh-CN-XiaoxiaoNeural)" value="zh-CN-XiaoxiaoNeural" />
                <el-option label="成熟男性 (zh-CN-YunxiNeural)" value="zh-CN-YunxiNeural" />
                <el-option label="活力女性 (zh-CN-XiaoyiNeural)" value="zh-CN-XiaoyiNeural" />
                <el-option label="正式男性 (zh-CN-YunyangNeural)" value="zh-CN-YunyangNeural" />
              </el-select>
            </el-form-item>
            <el-form-item label="语速">
              <el-slider v-model="config.tts.speed" :min="0.5" :max="2" :step="0.1" show-input />
            </el-form-item>
            <el-form-item label="音调">
              <el-slider v-model="config.tts.pitch" :min="0.5" :max="1.5" :step="0.1" show-input />
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const activeTab = ref('agent')

const config = reactive({
  agent: {
    triagePrompt: '你是一位专业的医院导诊助手。你的任务是根据用户的症状描述，引导用户选择合适的科室，并提供初步的健康建议。',
    qaPrompt: '你是一位博学且严谨的医疗百科专家。请基于知识库内容，为用户提供准确、易懂的医疗健康知识解答。',
    summaryPrompt: '你是一位资深的医疗速记员。请将医生与患者的对话内容整理成结构化的摘要，包含：主诉、现病史、既往史、初步判断。',
    encyclopediaPrompt: '你是一位医学百科助手。你的任务是解答各种医学名词、药物用途、检查项目等相关问题。'
  },
  model: {
    temperature: 0.7,
    maxTokens: 2048,
    topP: 0.95
  },
  tts: {
    voice: 'zh-CN-XiaoxiaoNeural',
    speed: 1.0,
    pitch: 1.0
  }
})

const handleSave = () => {
  try {
    localStorage.setItem('sys_config', JSON.stringify(config))
    ElMessage.success('配置已保存到本地')
  } catch (error) {
    ElMessage.error('保存失败')
  }
}

onMounted(() => {
  const saved = localStorage.getItem('sys_config')
  if (saved) {
    const parsed = JSON.parse(saved)
    Object.assign(config.agent, parsed.agent)
    Object.assign(config.model, parsed.model)
    Object.assign(config.tts, parsed.tts)
  }
})
</script>

<style scoped>
.system-config {
  padding: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  margin-top: 5px;
}
:deep(.el-tabs__content) {
  padding-top: 20px;
}
</style>
