# 09 - 管理端 + 医生端前端 (Vue3 + ElementPlus)

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 使用 Vue3 + ElementPlus + Pinia + Vue Router 构建管理端和医生端单页应用，包含完整的页面和 API 对接。

**Architecture:** 管理端和医生端共享一个 Vue3 项目，通过路由守卫 + 角色判断区分页面访问权限。使用 Axios 封装 HTTP 请求，Pinia 管理全局状态。

**Tech Stack:** Vue 3.4+, Vite 5, ElementPlus 2.x, Pinia, Vue Router 4, Axios, ECharts

**前置依赖:** 后端 03-08 全部服务就绪

---

## Task 1: 项目初始化

```bash
npm create vite@latest medical-admin -- --template vue
cd medical-admin
npm install element-plus @element-plus/icons-vue
npm install pinia vue-router@4 axios
npm install echarts
npm install nprogress
npm install @vueuse/core
```

---

## Task 2: 项目结构搭建

```
medical-admin/
├── src/
│   ├── api/                    # API 模块
│   │   ├── request.js          # Axios 封装
│   │   ├── auth.js             # 认证接口
│   │   ├── user.js             # 用户管理
│   │   ├── doctor.js           # 医生管理
│   │   ├── department.js       # 科室管理
│   │   ├── appointment.js      # 预约管理
│   │   ├── knowledge.js        # 知识库管理
│   │   └── chat.js             # 对话/百科
│   ├── components/             # 公共组件
│   │   ├── Layout/             # 布局组件
│   │   │   ├── AppLayout.vue   # 整体布局(侧边栏+头部+内容)
│   │   │   ├── Sidebar.vue     # 侧边栏菜单
│   │   │   └── Navbar.vue      # 顶部导航栏
│   │   ├── ChatPanel.vue       # 百科聊天面板(通用)
│   │   └── RichEditor.vue      # 富文本编辑器(医生简介)
│   ├── router/
│   │   └── index.js            # 路由配置 + 守卫
│   ├── stores/
│   │   ├── user.js             # 用户状态
│   │   └── app.js              # 应用状态(侧边栏折叠等)
│   ├── views/
│   │   ├── login/
│   │   │   └── LoginView.vue
│   │   ├── dashboard/
│   │   │   └── DashboardView.vue   # 数据看板
│   │   ├── admin/                   # 管理端页面
│   │   │   ├── UserManage.vue
│   │   │   ├── DoctorManage.vue
│   │   │   ├── DepartmentManage.vue
│   │   │   ├── AppointmentManage.vue
│   │   │   ├── KnowledgeBase.vue
│   │   │   ├── KnowledgeDocuments.vue
│   │   │   ├── ChatManage.vue
│   │   │   └── SystemConfig.vue
│   │   └── doctor/                  # 医生端页面
│   │       ├── MyProfile.vue
│   │       ├── MySchedule.vue
│   │       ├── MyAppointments.vue
│   │       ├── PatientSummary.vue
│   │       └── Encyclopedia.vue
│   ├── utils/
│   │   └── index.js
│   ├── App.vue
│   └── main.js
├── .env.development
├── .env.production
├── vite.config.js
└── package.json
```

---

## Task 3: Axios 封装 + 请求/响应拦截

**Files:**
- Create: `src/api/request.js`

```javascript
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000
})

// 请求拦截器
service.interceptors.request.use(config => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers['Authorization'] = `Bearer ${userStore.token}`
  }
  return config
})

// 响应拦截器
service.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.msg || '请求失败')
      if (res.code === 401) {
        useUserStore().logout()
        router.push('/login')
      }
      return Promise.reject(new Error(res.msg))
    }
    return res
  },
  error => {
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default service
```

---

## Task 4: Pinia 用户状态管理

**Files:**
- Create: `src/stores/user.js`

```javascript
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || 'null'))

  const isLogin = computed(() => !!token.value)
  const roles = computed(() => userInfo.value?.roles || [])
  const isAdmin = computed(() => roles.value.includes('ADMIN'))
  const isDoctor = computed(() => roles.value.includes('DOCTOR'))

  async function login(username, password) {
    const res = await loginApi({ username, password })
    token.value = res.data.token
    userInfo.value = res.data.user
    localStorage.setItem('token', token.value)
    localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  return { token, userInfo, isLogin, roles, isAdmin, isDoctor, login, logout }
})
```

---

## Task 5: 路由配置 + 权限守卫

**Files:**
- Create: `src/router/index.js`

路由分组：
- 公开路由：`/login`
- 管理端路由（requiresRole: ADMIN）：`/admin/*`
- 医生端路由（requiresRole: DOCTOR）：`/doctor/*`
- 共享路由：`/dashboard`

路由守卫：
```javascript
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  if (to.path === '/login') return next()
  if (!userStore.isLogin) return next('/login')
  if (to.meta.requiresRole && !userStore.roles.includes(to.meta.requiresRole)) {
    ElMessage.error('无权限访问')
    return next('/dashboard')
  }
  next()
})
```

---

## Task 6: 布局组件 (AppLayout + Sidebar + Navbar)

经典后台管理布局：
- 左侧固定侧边栏（可折叠），根据角色动态显示菜单项
- 顶部导航栏（用户头像、退出登录）
- 右侧内容区域 `<router-view />`

管理员菜单：数据看板、用户管理、医生管理、科室管理、预约管理、知识库管理、对话管理、系统配置
医生菜单：数据看板、我的画像、我的排班、预约患者、百科助手

---

## Task 7: 登录页面

**Files:**
- Create: `src/views/login/LoginView.vue`

ElementPlus Form + 用户名密码输入 + 登录按钮。登录成功后根据角色跳转到对应首页。

---

## Task 8: 数据看板 (Dashboard)

**Files:**
- Create: `src/views/dashboard/DashboardView.vue`

管理员看板：
- 统计卡片：今日预约数、活跃用户、AI对话量、知识库条目数
- ECharts 图表：最近7日预约趋势折线图、科室预约占比饼图

医生看板：
- 今日预约数、待接诊数
- 今日预约列表快速入口

---

## Task 9: 管理端 - 用户管理页面

**Files:**
- Create: `src/views/admin/UserManage.vue`

功能：
- 表格列表（分页、搜索）
- 列：用户名、昵称、手机、角色Tag、状态Switch、操作
- 操作：禁用/启用、分配角色（弹窗）
- 搜索框：关键词模糊搜索

---

## Task 10: 管理端 - 科室管理页面

**Files:**
- Create: `src/views/admin/DepartmentManage.vue`

功能：
- 表格 + 新增/编辑弹窗（名称、描述、图标、排序）
- 拖拽排序（可选）
- 启用/禁用

---

## Task 11: 管理端 - 医生管理页面

**Files:**
- Create: `src/views/admin/DoctorManage.vue`

功能：
- 表格列表（姓名、职称、科室Tag、擅长、状态）
- 新增/编辑弹窗（画像完整字段 + 科室多选）
- 查看排班按钮 → 跳转排班配置

---

## Task 12: 管理端 - 知识库管理页面

**Files:**
- Create: `src/views/admin/KnowledgeBase.vue`
- Create: `src/views/admin/KnowledgeDocuments.vue`

KnowledgeBase.vue：
- 知识库卡片列表（名称、文档数、分块数、状态）
- 新增知识库弹窗
- 点击进入文档管理页面

KnowledgeDocuments.vue：
- 文档列表（文件名、类型、大小、分块数、解析状态）
- 上传文档（拖拽上传）
- 查看分块（抽屉展示）
- 手动添加知识条目
- 重建索引按钮

---

## Task 13: 管理端 - 预约管理 + 对话管理

**Files:**
- Create: `src/views/admin/AppointmentManage.vue`
- Create: `src/views/admin/ChatManage.vue`

预约管理：表格 + 筛选（日期、科室、状态）+ 查看详情
对话管理：对话记录列表 + 查看对话详情（消息列表展示）

---

## Task 14: 管理端 - 系统配置

**Files:**
- Create: `src/views/admin/SystemConfig.vue`

Tab 切换：
- Agent 配置：编辑各 Agent 的 System Prompt
- 模型参数：temperature、maxTokens
- TTS 配置：音色、语速

---

## Task 15: 医生端 - 我的画像

**Files:**
- Create: `src/views/doctor/MyProfile.vue`

表单：姓名、职称（下拉）、头像（上传）、擅长（Tag输入）、主治方向（Tag输入）、简介（富文本）
预览模式 + 编辑模式切换

---

## Task 16: 医生端 - 预约患者 + 对话摘要

**Files:**
- Create: `src/views/doctor/MyAppointments.vue`
- Create: `src/views/doctor/PatientSummary.vue`

MyAppointments.vue：
- 日期选择器（默认今天）
- 预约列表：患者昵称、预约时间、排队号、状态
- 每行有"查看对话摘要"按钮

PatientSummary.vue：
- 结构化摘要展示：主诉、伴随症状、持续时间、严重程度、既往史、AI判断
- 完整对话记录折叠展示

---

## Task 17: 医生端 - 百科助手

**Files:**
- Create: `src/views/doctor/Encyclopedia.vue`

左侧：会话列表
右侧：聊天界面（SSE 流式展示 AI 回复）
- EventSource 接收 SSE
- Markdown 渲染 AI 回复
- 新建对话 / 删除对话

---

## Task 18: 编译验证

```bash
cd medical-admin && npm run build
```

Expected: dist 目录生成，无编译错误

```bash
git add .
git commit -m "feat(frontend-admin): implement admin and doctor web portal with Vue3 + ElementPlus"
```

---

## 检查清单

- [ ] Axios 封装 + Token 拦截 + 401 自动跳转
- [ ] Pinia 用户状态 + 角色判断
- [ ] 路由守卫 + 角色权限控制
- [ ] 布局组件（侧边栏菜单按角色动态）
- [ ] 登录页面
- [ ] 数据看板（ECharts 统计图表）
- [ ] 用户管理（CRUD + 角色分配）
- [ ] 科室管理（CRUD）
- [ ] 医生管理（CRUD + 科室关联）
- [ ] 知识库管理（CRUD + 文档上传 + 分块预览 + 重建索引）
- [ ] 预约管理（列表 + 筛选 + 详情）
- [ ] 对话管理（对话列表 + 消息详情）
- [ ] 系统配置（Agent Prompt + 模型参数 + TTS 配置）
- [ ] 医生画像自维护
- [ ] 医生预约患者列表 + 对话摘要
- [ ] 医生百科助手（SSE 聊天）
- [ ] `npm run build` 通过
