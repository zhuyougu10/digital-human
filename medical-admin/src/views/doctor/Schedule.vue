<template>
  <div class="doctor-schedule">
    <el-card shadow="never">
      <div class="toolbar">
        <el-form :inline="true" :model="queryForm">
          <el-form-item label="号源日期">
            <el-date-picker v-model="queryForm.date" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
          </el-form-item>
          <el-form-item><el-button type="primary" @click="fetchAvailableSlots">查询号源</el-button></el-form-item>
        </el-form>
        <div class="toolbar-actions">
          <el-button type="success" @click="templateDialog.visible = true">新增排班模板</el-button>
          <el-button type="primary" @click="openGenerateDialog">生成号源</el-button>
        </div>
      </div>
    </el-card>

    <el-row :gutter="20" class="m-t-20">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" v-loading="templateLoading">
          <template #header>
            <div class="card-header">
              <span>排班模板（周视图）</span>
              <el-tag type="info">医生ID: {{ doctorId || '-' }}</el-tag>
            </div>
          </template>
          <el-table :data="weeklyRows" border>
            <el-table-column prop="weekdayLabel" label="星期" width="100" />
            <el-table-column label="时段">
              <template #default="{ row }">
                <template v-if="row.items.length">
                  <div v-for="item in row.items" :key="item.id || `${item.weekday}-${item.startTime}`" class="slot-line">
                    <el-tag size="small">{{ item.startTime }} - {{ item.endTime }}</el-tag>
                    <span class="slot-capacity">容量: {{ item.maxPatients || '-' }}</span>
                    <el-tag :type="item.enabled ? 'success' : 'info'" size="small">{{ item.enabled ? '启用' : '停用' }}</el-tag>
                  </div>
                </template>
                <span v-else class="empty-text">未配置</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="10">
        <el-card shadow="never" v-loading="slotLoading">
          <template #header>
            <div class="card-header">
              <span>可用号源</span>
              <el-tag>{{ queryForm.date || '-' }}</el-tag>
            </div>
          </template>
          <el-table :data="slotList" border>
            <el-table-column prop="startTime" label="开始" width="90" />
            <el-table-column prop="endTime" label="结束" width="90" />
            <el-table-column prop="remaining" label="剩余号源" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.remaining > 0 ? 'success' : 'danger'" size="small">{{ row.remaining > 0 ? '可预约' : '约满' }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!slotList.length" description="暂无号源数据" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="templateDialog.visible" title="新增排班模板" width="520px" @closed="resetTemplateDialog">
      <el-form ref="templateFormRef" :model="templateDialog.form" :rules="templateRules" label-width="100px">
        <el-form-item label="星期" prop="weekday">
          <el-select v-model="templateDialog.form.weekday" placeholder="请选择星期" style="width: 100%">
            <el-option v-for="item in weekOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间段" required>
          <div class="time-range">
            <el-time-picker v-model="templateDialog.form.startTime" value-format="HH:mm:ss" placeholder="开始时间" style="width: 48%" />
            <span class="time-separator">-</span>
            <el-time-picker v-model="templateDialog.form.endTime" value-format="HH:mm:ss" placeholder="结束时间" style="width: 48%" />
          </div>
        </el-form-item>
        <el-form-item label="最大接诊数" prop="maxPatients"><el-input-number v-model="templateDialog.form.maxPatients" :min="1" :max="200" /></el-form-item>
        <el-form-item label="状态" prop="enabled"><el-switch v-model="templateDialog.form.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="templateDialog.loading" @click="saveTemplate">保存模板</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="generateDialog.visible" title="生成号源" width="520px">
      <el-form ref="generateFormRef" :model="generateDialog.form" :rules="generateRules" label-width="100px">
        <el-form-item label="日期范围" prop="dateRange">
          <el-date-picker
            v-model="generateDialog.form.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="generateDialog.loading" @click="handleGenerateSlots">开始生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getScheduleTemplates, createScheduleTemplate, generateSlots, getAvailableSlots, getDoctorList } from '@/api/doctor'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const doctorId = ref(null)
const templateLoading = ref(false)
const slotLoading = ref(false)
const scheduleTemplates = ref([])
const slotList = ref([])
const templateFormRef = ref(null)
const generateFormRef = ref(null)

const queryForm = reactive({ date: '' })
const templateDialog = reactive({
  visible: false, loading: false,
  form: { weekday: 1, startTime: '', endTime: '', maxPatients: 20, enabled: true }
})
const generateDialog = reactive({ visible: false, loading: false, form: { dateRange: [] } })

const weekOptions = [
  { label: '周一', value: 1 }, { label: '周二', value: 2 }, { label: '周三', value: 3 }, { label: '周四', value: 4 },
  { label: '周五', value: 5 }, { label: '周六', value: 6 }, { label: '周日', value: 7 }
]
const templateRules = { weekday: [{ required: true, message: '请选择星期', trigger: 'change' }], maxPatients: [{ required: true, message: '请设置最大接诊数', trigger: 'change' }] }
const generateRules = { dateRange: [{ type: 'array', required: true, message: '请选择日期范围', trigger: 'change' }] }

const normalizeTemplate = (item = {}) => ({
  ...item,
  weekday: Number(item.weekday || item.dayOfWeek || 1),
  startTime: (item.startTime || '').slice(0, 8),
  endTime: (item.endTime || '').slice(0, 8),
  maxPatients: item.maxPatients || item.maxAppointments || 0,
  enabled: typeof item.enabled === 'boolean' ? item.enabled : item.status !== 0
})

const weeklyRows = computed(() => {
  const grouped = scheduleTemplates.value.reduce((acc, cur) => {
    if (!acc[cur.weekday]) acc[cur.weekday] = []
    acc[cur.weekday].push(cur)
    return acc
  }, {})
  return weekOptions.map(day => ({ weekday: day.value, weekdayLabel: day.label, items: (grouped[day.value] || []).sort((a, b) => a.startTime.localeCompare(b.startTime)) }))
})

const resolveDoctorId = async () => {
  const info = userStore.userInfo || {}
  if (info.doctorId) return info.doctorId
  const res = await getDoctorList({ pageNum: 1, pageSize: 100, keyword: info.username || info.nickname || '' })
  const list = res.data?.list || []
  const match = list.find(item => item.userId === info.id) || list.find(item => item.name === info.nickname) || list[0]
  return match?.id || null
}

const fetchTemplates = async () => {
  if (!doctorId.value) return
  templateLoading.value = true
  try {
    const res = await getScheduleTemplates(doctorId.value)
    const list = Array.isArray(res.data) ? res.data : (res.data?.list || [])
    scheduleTemplates.value = list.map(normalizeTemplate)
  } catch (error) {
    console.error('Failed to fetch templates:', error)
  } finally {
    templateLoading.value = false
  }
}

const fetchAvailableSlots = async () => {
  if (!doctorId.value || !queryForm.date) return ElMessage.warning('请先选择日期')
  slotLoading.value = true
  try {
    const res = await getAvailableSlots({ doctorId: doctorId.value, date: queryForm.date })
    const list = Array.isArray(res.data) ? res.data : (res.data?.list || [])
    slotList.value = list.map(item => ({
      ...item,
      startTime: (item.startTime || '').slice(0, 8),
      endTime: (item.endTime || '').slice(0, 8),
      remaining: item.remaining ?? item.availableCount ?? 0
    }))
  } catch (error) {
    console.error('Failed to fetch available slots:', error)
  } finally {
    slotLoading.value = false
  }
}

const resetTemplateDialog = () => { templateDialog.form = { weekday: 1, startTime: '', endTime: '', maxPatients: 20, enabled: true } }
const openGenerateDialog = () => { generateDialog.form.dateRange = []; generateDialog.visible = true }

const saveTemplate = async () => {
  if (!templateFormRef.value || !doctorId.value) return
  if (!templateDialog.form.startTime || !templateDialog.form.endTime) return ElMessage.warning('请选择完整时间段')
  await templateFormRef.value.validate(async valid => {
    if (!valid) return
    templateDialog.loading = true
    try {
      const payload = {
        weekday: templateDialog.form.weekday, dayOfWeek: templateDialog.form.weekday,
        startTime: templateDialog.form.startTime, endTime: templateDialog.form.endTime,
        maxPatients: templateDialog.form.maxPatients, maxAppointments: templateDialog.form.maxPatients,
        status: templateDialog.form.enabled ? 1 : 0, enabled: templateDialog.form.enabled
      }
      await createScheduleTemplate(doctorId.value, payload)
      ElMessage.success('排班模板已保存')
      templateDialog.visible = false
      await fetchTemplates()
    } catch (error) {
      console.error('Failed to create template:', error)
    } finally {
      templateDialog.loading = false
    }
  })
}

const handleGenerateSlots = async () => {
  if (!generateFormRef.value) return
  await generateFormRef.value.validate(async valid => {
    if (!valid) return
    generateDialog.loading = true
    try {
      const [startDate, endDate] = generateDialog.form.dateRange
      await generateSlots({ startDate, endDate })
      ElMessage.success('号源生成完成')
      generateDialog.visible = false
      if (queryForm.date) fetchAvailableSlots()
    } catch (error) {
      console.error('Failed to generate slots:', error)
    } finally {
      generateDialog.loading = false
    }
  })
}

onMounted(async () => {
  doctorId.value = await resolveDoctorId()
  if (!doctorId.value) return ElMessage.warning('未找到医生档案，排班功能暂不可用')
  queryForm.date = new Date().toISOString().slice(0, 10)
  await fetchTemplates()
  await fetchAvailableSlots()
})
</script>

<style scoped>
.doctor-schedule { padding: 20px; }
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 12px; }
.toolbar-actions { display: flex; gap: 8px; }
.m-t-20 { margin-top: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.slot-line { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.slot-capacity { color: #606266; font-size: 12px; }
.empty-text { color: #909399; }
.time-range { width: 100%; display: flex; align-items: center; justify-content: space-between; }
.time-separator { width: 4%; text-align: center; }
</style>
