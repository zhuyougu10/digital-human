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
