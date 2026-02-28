<template>
  <div class="rich-editor">
    <div class="editor-toolbar">
      <el-button-group>
        <el-tooltip content="加粗" placement="top">
          <el-button size="small" @click="execCommand('bold')">
            <el-icon><EditPen /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip content="斜体" placement="top">
          <el-button size="small" @click="execCommand('italic')">
            <el-icon><Edit /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip content="无序列表" placement="top">
          <el-button size="small" @click="execCommand('insertUnorderedList')">
            <el-icon><List /></el-icon>
          </el-button>
        </el-tooltip>
      </el-button-group>
    </div>
    <div
      ref="editorRef"
      class="editor-content"
      contenteditable="true"
      @input="handleInput"
      @blur="handleBlur"
      v-html="innerValue"
    ></div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { EditPen, Edit, List } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue'])

const editorRef = ref(null)
const innerValue = ref(props.modelValue)
const isFocused = ref(false)

watch(() => props.modelValue, (newVal) => {
  if (!isFocused.value && newVal !== editorRef.value.innerHTML) {
    innerValue.value = newVal
  }
})

const execCommand = (command) => {
  document.execCommand(command, false, null)
  editorRef.value.focus()
  handleInput()
}

const handleInput = () => {
  const content = editorRef.value.innerHTML
  emit('update:modelValue', content)
}

const handleBlur = () => {
  isFocused.value = false
}

onMounted(() => {
  editorRef.value.addEventListener('focus', () => {
    isFocused.value = true
  })
})
</script>

<style scoped>
.rich-editor {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
  width: 100%;
}

.editor-toolbar {
  padding: 8px;
  background: #f5f7fa;
  border-bottom: 1px solid #dcdfe6;
}

.editor-content {
  min-height: 200px;
  max-height: 500px;
  padding: 12px;
  outline: none;
  overflow-y: auto;
  line-height: 1.6;
}

.editor-content:focus {
  background: #fff;
}

:deep(ul) {
  padding-left: 20px;
}

:deep(li) {
  list-style-type: disc;
}
</style>
