<template>
  <div class="doctor-profile">
    <el-card shadow="never">
      <div class="header-content">
        <div class="title-group">
          <h2>我的画像</h2>
          <p>维护医生基础信息、擅长领域与专业简介</p>
        </div>
        <div class="actions">
          <el-button v-if="!isEditing" type="primary" @click="startEdit">编辑资料</el-button>
          <template v-else>
            <el-button @click="cancelEdit">取消</el-button>
            <el-button type="primary" :loading="saving" @click="saveProfile">保存</el-button>
          </template>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="m-t-20" v-loading="loading">
      <template v-if="!isEditing">
        <el-row :gutter="20">
          <el-col :xs="24" :sm="8" :md="6">
            <div class="avatar-wrap">
              <el-avatar :size="120" :src="profile.avatar">{{ profile.name?.charAt(0) || '医' }}</el-avatar>
            </div>
          </el-col>
          <el-col :xs="24" :sm="16" :md="18">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="姓名">{{ profile.name || '-' }}</el-descriptions-item>
              <el-descriptions-item label="职称">
                <el-tag>{{ profile.title || '-' }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="擅长">
                <el-tag v-for="item in profile.specialties" :key="`sp-${item}`" class="m-r-5 m-b-5">{{ item }}</el-tag>
                <span v-if="!profile.specialties.length">-</span>
              </el-descriptions-item>
              <el-descriptions-item label="主治方向">
                <el-tag v-for="item in profile.focus" :key="`fc-${item}`" type="success" class="m-r-5 m-b-5">{{ item }}</el-tag>
                <span v-if="!profile.focus.length">-</span>
              </el-descriptions-item>
              <el-descriptions-item label="简介"><div class="intro-preview" v-html="profile.introduction || '<p>-</p>'"></div></el-descriptions-item>
            </el-descriptions>
          </el-col>
        </el-row>
      </template>

      <el-form v-else ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="20">
          <el-col :xs="24" :md="12">
            <el-form-item label="姓名" prop="name">
              <el-input v-model="form.name" placeholder="请输入姓名" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="职称" prop="title">
              <el-select v-model="form.title" placeholder="请选择职称" style="width: 100%">
                <el-option v-for="item in titleOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="头像">
          <el-upload action="#" :show-file-list="false" :auto-upload="false" :on-change="handleAvatarChange">
            <el-avatar :size="96" :src="form.avatar" class="avatar-edit">{{ form.name?.charAt(0) || '医' }}</el-avatar>
            <div class="upload-text">点击上传头像</div>
          </el-upload>
        </el-form-item>

        <el-form-item label="擅长" prop="specialties">
          <div class="tag-input">
            <el-tag v-for="item in form.specialties" :key="`es-${item}`" closable class="m-r-5 m-b-5" @close="removeTag('specialties', item)">
              {{ item }}
            </el-tag>
            <el-input v-model="tagInput.specialties" placeholder="输入后回车添加" @keyup.enter="addTag('specialties')" @blur="addTag('specialties')" />
          </div>
        </el-form-item>

        <el-form-item label="主治方向" prop="focus">
          <div class="tag-input">
            <el-tag v-for="item in form.focus" :key="`ef-${item}`" type="success" closable class="m-r-5 m-b-5" @close="removeTag('focus', item)">
              {{ item }}
            </el-tag>
            <el-input v-model="tagInput.focus" placeholder="输入后回车添加" @keyup.enter="addTag('focus')" @blur="addTag('focus')" />
          </div>
        </el-form-item>

        <el-form-item label="简介" prop="introduction">
          <RichEditor v-model="form.introduction" />
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getDoctorById, getDoctorList, updateDoctor } from '@/api/doctor'
import { useUserStore } from '@/stores/user'
import RichEditor from '@/components/RichEditor.vue'

const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const isEditing = ref(false)
const formRef = ref(null)
const doctorId = ref(null)

const titleOptions = ['主任医师', '副主任医师', '主治医师', '住院医师']
const profile = reactive({ name: '', title: '', avatar: '', specialties: [], focus: [], introduction: '', departmentIds: [] })
const form = reactive({ name: '', title: '', avatar: '', specialties: [], focus: [], introduction: '', departmentIds: [] })
const tagInput = reactive({ specialties: '', focus: '' })

const rules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  title: [{ required: true, message: '请选择职称', trigger: 'change' }],
  specialties: [{ type: 'array', required: true, message: '请至少添加一个擅长领域', trigger: 'change' }],
  focus: [{ type: 'array', required: true, message: '请至少添加一个主治方向', trigger: 'change' }]
}

const splitTags = (value) => {
  if (Array.isArray(value)) return value.filter(Boolean)
  if (!value) return []
  return String(value).split(/[，,]/).map(item => item.trim()).filter(Boolean)
}

const mapDoctorToState = (raw = {}) => ({
  name: raw.name || '',
  title: raw.title || '',
  avatar: raw.avatar || '',
  specialties: splitTags(raw.specialties),
  focus: splitTags(raw.focus),
  introduction: raw.introduction || raw.description || '',
  departmentIds: raw.departments?.map(item => item.id) || raw.departmentIds || []
})

const syncProfile = (state) => {
  Object.assign(profile, state)
  Object.assign(form, JSON.parse(JSON.stringify(state)))
}

const resolveDoctorId = async () => {
  const info = userStore.userInfo || {}
  if (info.doctorId) return info.doctorId

  const res = await getDoctorList({ pageNum: 1, pageSize: 100, keyword: info.username || info.nickname || '' })
  const list = res.data?.list || []
  const match = list.find(item => item.userId === info.id) || list.find(item => item.name === info.nickname) || list[0]
  return match?.id || null
}

const fetchDoctorProfile = async () => {
  loading.value = true
  try {
    const id = await resolveDoctorId()
    if (!id) {
      ElMessage.warning('未找到当前医生档案，请联系管理员关联账号')
      return
    }
    doctorId.value = id
    const res = await getDoctorById(id)
    const state = mapDoctorToState(res.data)
    syncProfile(state)
  } catch (error) {
    console.error('Failed to load doctor profile:', error)
  } finally {
    loading.value = false
  }
}

const startEdit = () => {
  Object.assign(form, JSON.parse(JSON.stringify(profile)))
  isEditing.value = true
}

const cancelEdit = () => {
  isEditing.value = false
  tagInput.specialties = ''
  tagInput.focus = ''
  Object.assign(form, JSON.parse(JSON.stringify(profile)))
}

const addTag = (field) => {
  const value = tagInput[field].trim()
  if (!value) return
  if (!form[field].includes(value)) form[field].push(value)
  tagInput[field] = ''
}

const removeTag = (field, value) => { form[field] = form[field].filter(item => item !== value) }

const handleAvatarChange = (uploadFile) => {
  const file = uploadFile.raw
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    form.avatar = reader.result
  }
  reader.readAsDataURL(file)
}

const saveProfile = async () => {
  if (!formRef.value || !doctorId.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = {
        ...form,
        specialties: form.specialties.join(','),
        focus: form.focus.join(','),
        introduction: form.introduction,
        description: form.introduction
      }
      await updateDoctor(doctorId.value, payload)
      syncProfile(mapDoctorToState(payload))
      isEditing.value = false
      ElMessage.success('医生画像已更新')
    } catch (error) {
      console.error('Failed to save doctor profile:', error)
    } finally {
      saving.value = false
    }
  })
}

onMounted(() => {
  fetchDoctorProfile()
})
</script>

<style scoped>
.doctor-profile { padding: 20px; }
.header-content { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.title-group h2 { margin: 0; }
.title-group p { margin: 6px 0 0; color: #909399; }
.m-t-20 { margin-top: 20px; }
.m-r-5 { margin-right: 5px; }
.m-b-5 { margin-bottom: 5px; }
.avatar-wrap { display: flex; justify-content: center; margin-bottom: 20px; }
.tag-input { width: 100%; }
.avatar-edit { margin-bottom: 8px; }
.upload-text { color: #909399; font-size: 12px; }
.intro-preview :deep(p) { margin: 0 0 8px; }
</style>
