import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/components/Layout/AppLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '数据看板' }
      },
      // Admin Routes
      {
        path: 'admin/users',
        name: 'UserManagement',
        component: () => import('@/views/admin/UserManagement.vue'),
        meta: { title: '用户管理', requiresRole: 'ADMIN' }
      },
      {
        path: 'admin/departments',
        name: 'DepartmentManagement',
        component: () => import('@/views/admin/DepartmentManagement.vue'),
        meta: { title: '科室管理', requiresRole: 'ADMIN' }
      },
      {
        path: 'admin/doctors',
        name: 'DoctorManagement',
        component: () => import('@/views/admin/DoctorManagement.vue'),
        meta: { title: '医生管理', requiresRole: 'ADMIN' }
      },
      {
        path: 'admin/knowledge',
        name: 'KnowledgeBase',
        component: () => import('@/views/admin/KnowledgeBase.vue'),
        meta: { title: '知识库管理', requiresRole: 'ADMIN' }
      },
      {
        path: 'admin/documents',
        name: 'DocumentManagement',
        component: () => import('@/views/admin/DocumentManagement.vue'),
        meta: { title: '文档管理', requiresRole: 'ADMIN' }
      },
      {
        path: 'admin/appointments',
        name: 'AppointmentManagement',
        component: () => import('@/views/admin/AppointmentManagement.vue'),
        meta: { title: '预约管理', requiresRole: 'ADMIN' }
      },
      {
        path: 'admin/doctor-schedule/:doctorId?',
        name: 'AdminDoctorSchedule',
        component: () => import('@/views/doctor/Schedule.vue'),
        meta: { title: '医生排班', requiresRole: 'ADMIN' }
      },
      {
        path: 'admin/conversations',
        name: 'ConversationManagement',
        component: () => import('@/views/admin/ConversationManagement.vue'),
        meta: { title: '对话管理', requiresRole: 'ADMIN' }
      },
      {
        path: 'admin/config',
        name: 'SystemConfig',
        component: () => import('@/views/admin/SystemConfig.vue'),
        meta: { title: '系统配置', requiresRole: 'ADMIN' }
      },
      // Doctor Routes
      {
        path: 'doctor/profile',
        name: 'DoctorProfile',
        component: () => import('@/views/doctor/Profile.vue'),
        meta: { title: '我的画像', requiresRole: 'DOCTOR' }
      },
      {
        path: 'doctor/schedule',
        name: 'DoctorSchedule',
        component: () => import('@/views/doctor/Schedule.vue'),
        meta: { title: '我的排班', requiresRole: 'DOCTOR' }
      },
      {
        path: 'doctor/appointments',
        name: 'DoctorAppointments',
        component: () => import('@/views/doctor/Appointments.vue'),
        meta: { title: '预约患者', requiresRole: 'DOCTOR' }
      },
      {
        path: 'doctor/patient-summary/:id?',
        name: 'PatientSummary',
        component: () => import('@/views/doctor/PatientSummary.vue'),
        meta: { title: '患者摘要', requiresRole: 'DOCTOR' }
      },
      {
        path: 'doctor/assistant',
        name: 'MedicalAssistant',
        component: () => import('@/views/doctor/Assistant.vue'),
        meta: { title: '百科助手', requiresRole: 'DOCTOR' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  
  if (to.path === '/login') {
    if (userStore.isLogin) {
      next('/')
    } else {
      next()
    }
    return
  }

  if (!userStore.isLogin) {
    next('/login')
    return
  }

  if (to.meta.requiresRole) {
    const hasRole = userStore.roles.includes(to.meta.requiresRole)
    if (!hasRole) {
      ElMessage.error('权限不足')
      next('/dashboard')
      return
    }
  }

  next()
})

export default router
