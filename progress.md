# Progress

## Session: 2026-04-13 01:45

### Actions
- 分析了现有 live2d-h5 实现（pixi.js + pixi-live2d-display + Cubism 4）
- 分析了 chat.vue web-view 嵌入方案
- 确认小程序 canvas type="webgl" 可用
- 创建 task_plan.md、findings.md、progress.md
- 决定用 Cubism SDK 底层 WebGL 渲染，跳过 pixi

### Next
- 创建 GitHub issues
- 派发 Codex 执行 Phase 1 + Phase 2

## Session: 2026-04-13 09:15

### Root Cause
- `medical-admin/src/views/admin/UserManagement.vue` 分页查询参数使用了 `page` / `size`，后端分页对象实际接收 `pageNum` / `pageSize`，导致用户列表翻页与每页条数变更失效。
- `AppointmentController.doctorAppointments()` 直接把 Sa-Token 登录用户 id 当作 `doctorId` 传给预约查询，但预约表存的是医生档案 `doctor_profile.id`，不是用户表 `sys_user.id`。

### Fix
- 将用户管理页分页状态与 `el-pagination` 绑定统一改为 `pageNum` / `pageSize`，确保请求参数与后端契约一致。
- 在预约服务控制器中通过 `RemoteDoctorService.getDoctorByUserId(userId)` 先解析医生档案，再使用医生档案 id 查询医生预约；解析失败时记录错误日志并抛出 `DOCTOR_NOT_FOUND`。

## Session: 2026-04-13 19:23

### Root Cause
- `TriageAgent` 的系统提示词没有注入服务器当前时间，模型在处理“今天/明天”这类相对日期时会自行编造日期。
- `DoctorProfileServiceImpl.searchBySymptom()` 只对 `specialties` / `treatmentAreas` 做 `LIKE` 查询，常见症状词无法稳定映射到系统内真实存在的科室与可预约医生。
- 导诊提示词未限制可推荐的科室集合，模型会给出系统中不存在的科室名称。

### Fix
- 在 `TriageAgent` 中动态注入服务器当前时间和时区，并明确要求相对日期必须以服务器时间为准。
- 为医生搜索增加症状到系统科室的映射层，通过真实科室关联反查医生，再与原有文本匹配结果合并去重。
- 在导诊提示词中限定仅可推荐系统现有科室：内科、外科、神经内科、儿科、妇产科、眼科、耳鼻喉科、皮肤科、中医科、口腔科。
