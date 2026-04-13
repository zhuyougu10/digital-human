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

## Session: 2026-04-13 19:59

### Root Cause
- 后台登录 502 只出现在 `http://127.0.0.1/api/user/auth/login` 经由 `medical-admin-web` 的 nginx 代理入口，直接请求 gateway `http://127.0.0.1:8080/api/user/auth/login` 返回 200，说明 user-service 与 gateway 本身正常。
- `medical-admin/nginx.conf` 使用固定上游名 `proxy_pass http://medical-gateway:8080/api/;`。nginx 在容器启动时解析一次 Docker DNS，gateway 容器重建后 IP 变化，但旧的 `medical-admin-web` 容器不会自动重新解析，导致继续转发到失效 IP，表现为 502。
- 手工 `docker restart medical-admin-web` 后登录立刻恢复 200，进一步确认是 admin-web 内部 nginx 的上游 DNS 缓存/静态解析问题。

### Fix Plan
- 发布 issue，要求修复 admin-web 对 gateway 的动态解析或容器编排策略，确保 gateway 重建后无需手工重启 admin-web。
- 修复后验证 admin-web 入口登录恢复，且在 gateway 重建后仍然正常。

## Session: 2026-04-13 20:20

### Root Cause
- `TriageAgent` 的可推荐科室名单硬编码在 Java 常量里，新增或停用科室后，导诊提示词不会自动反映数据库中的真实 `department` 表状态。
- `DoctorProfileServiceImpl` 的症状到科室映射虽然命中了固定科室名，但没有再校验这些科室当前是否仍处于启用状态、是否真实存在于数据库，因此搜索可能继续使用失效科室名称。

### Fix
- 在 doctor-service 新增 `/doctor/inner/departments/names` 内部接口，按 `status = 0` 从 `department` 表读取启用科室名称，并通过 `RemoteDoctorService` 暴露给其他服务。
- 将 `TriageAgent` 改为通过 Feign 动态拉取科室名称，并增加短时缓存与回退默认值，避免单次导诊对 doctor-service 故障过于敏感。
- 在 `DoctorProfileServiceImpl.resolveDepartmentNamesBySymptoms()` 中，将静态映射产出的科室名与数据库里真实启用科室求交集，再继续做医生匹配。
