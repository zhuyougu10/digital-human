<template>
  <div class="rich-editor" :class="{ 'is-focused': isFocused }">
    <div class="editor-toolbar">
      <el-button-group>
        <el-tooltip content="加粗" placement="top">
          <el-button size="small" @click.prevent="execCommand('bold')">
            <el-icon><EditPen /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip content="斜体" placement="top">
          <el-button size="small" @click.prevent="execCommand('italic')">
            <el-icon><Edit /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip content="下划线" placement="top">
          <el-button size="small" @click.prevent="execCommand('underline')">
            <el-icon><Bottom /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip content="无序列表" placement="top">
          <el-button size="small" @click.prevent="execCommand('insertUnorderedList')">
            <el-icon><List /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip content="有序列表" placement="top">
          <el-button size="small" @click.prevent="execCommand('insertOrderedList')">
            <el-icon><Expand /></el-icon>
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
      @focus="handleFocus"
    ></div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { EditPen, Edit, List, Expand, Bottom } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue'])

const editorRef = ref(null)
const isFocused = ref(false)

// Watch for external changes
watch(() => props.modelValue, (newVal) => {
  if (editorRef.value && newVal !== editorRef.value.innerHTML) {
    // Only update if not focused to avoid cursor jumping
    // Or if the content is significantly different (e.g. reset)
    if (!isFocused.value) {
      editorRef.value.innerHTML = newVal
    } else if (newVal === '' && editorRef.value.innerHTML !== '') {
        // Allow clearing even if focused
        editorRef.value.innerHTML = ''
    }
  }
})

const execCommand = (command) => {
  document.execCommand(command, false, null)
  if (editorRef.value) {
    editorRef.value.focus()
    handleInput()
  }
}

const handleInput = () => {
  const content = editorRef.value.innerHTML
  emit('update:modelValue', content)
}

const handleFocus = () => {
  isFocused.value = true
}

const handleBlur = () => {
  isFocused.value = false
}

onMounted(() => {
  if (editorRef.value) {
    editorRef.value.innerHTML = props.modelValue || ''
  }
})
</script>

<style scoped>
.rich-editor {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  overflow: hidden;
  width: 100%;
  transition: border-color 0.2s, box-shadow 0.2s;
  background: #fff;
}

.rich-editor.is-focused {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.1);
}

.editor-toolbar {
  padding: 8px 12px;
  background: #F9FAFB;
  border-bottom: 1px solid var(--border-color-light);
  display: flex;
  gap: 8px;
}

.editor-content {
  min-height: 150px;
  max-height: 400px;
  padding: 12px;
  outline: none;
  overflow-y: auto;
  line-height: 1.6;
  font-size: 14px;
  color: var(--text-primary);
}

.editor-content:empty:before {
  content: attr(placeholder);
  color: var(--text-placeholder);
  display: block; /* For Firefox */
}

:deep(ul), :deep(ol) {
  padding-left: 20px;
  margin: 8px 0;
}

:deep(li) {
  margin-bottom: 4px;
}

:deep(b), :deep(strong) {
  font-weight: 600;
}
</style>
