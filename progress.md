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

## Session: 2026-04-13 21:05

### Root Cause
- `medical-mp/src/utils/sse.js` 强制设置 `responseType: 'arraybuffer'`，配合 `enableChunked` 时会增加小程序端整包缓冲风险，导致真实设备上 `onChunkReceived` 不能稳定逐块触发。
- 当前 SSE 解析直接对全文做 `split(/\n\n/)`。当单个事件跨 chunk，或者 `data:` 负载里携带换行语义时，这种做法会把事件边界切错，直接表现为消息直到结尾才一次性拼出来。
- UTF-8 解码是一次一块直接转字符串，没有保留尾部不完整多字节序列；中文字符若恰好被拆到两个 chunk，会造成流式文本损坏，进一步干扰 SSE 逐行解析。
- `TtsPlayer.vue` 没有输出 `InnerAudioContext` 的实际错误信息，也没有拦截非绝对音频地址，导致播放失败时页面只看到静默失败。

### Fix
- 将小程序 SSE 请求改为依赖 `enableChunked` 原生分块，去掉显式 `responseType`，同时兼容字符串和 `ArrayBuffer` chunk。
- 将 SSE 解析改为按行累积、空行提交事件，并增加尾部 UTF-8 字节缓冲，保证跨 chunk 中文和事件边界都能正确恢复。
- 在聊天页统一把服务端返回的相对 `ttsUrl` 转成完整网关地址后再入队播放，避免组件拿到相对路径。
- 在 `TtsPlayer.vue` 中补充绝对地址校验与 `onError` 详细日志，便于定位真实设备播放失败原因。

## Session: 2026-04-14 01:41

### New Approved Scope
- 主人确认开始 `/skill coding-brain` 执行新一轮需求。
- 本轮范围有两部分：
  1. 把 `https://live2d.zhuyougu.cn` 从旧 H5 页面改造成受保护的模型资源站
  2. 数字人统一命名为 `安禾`

### Planning Notes
- 已完成设计文档：`docs/superpowers/specs/2026-04-14-live2d-asset-protection-and-naming-design.md`
- 当前进入实现计划阶段，下一步按 coding-brain 路线拆 backend / frontend / deployment 任务。

## Session: 2026-04-14 10:20

### Root Cause
- `medical-mp/src/lib/cubism-renderer.js` 只对 Live2D 资源使用裸 `wx.request` / `image.src`。这条链路没有复用小程序现有登录态请求头，因此受保护的 `.model3.json`、`.moc3`、动作、表情、物理文件都会缺少 `Authorization`。
- 纹理资源即使切到受保护域名，`canvas.createImage().src = remoteUrl` 也无法附带自定义请求头，导致图片请求会继续以匿名方式发出，鉴权后必然失败。
- `medical-mp/src/pages/chat/chat.vue` 聊天页欢迎语仍使用泛称 “AI医疗助手”，页面顶部也没有显式展示数字人姓名，无法满足统一命名为 “安禾” 的要求。

### Fix
- 在 `medical-mp/src/api/request.js` 提取通用 token/header 构造函数，供普通 API 请求和 Live2D 资源加载共用同一份 `Authorization: Bearer <token>` 逻辑。
- 将 Live2D 文本/二进制资源加载统一改为带鉴权头的 `wx.request`，并把纹理资源改为 `wx.downloadFile + filePath + image.src=本地路径`，绕过小程序远程图片无法带 header 的限制。
- 为 Live2D 资源请求增加按资源类型输出的诊断日志，便于区分 JSON、moc3、纹理等具体失败点。
- 在聊天页顶部显式展示数字人姓名，并把欢迎语统一改为 “安禾”。

## Session: 2026-04-14 10:30

### Root Cause
- `medical-mp/live2d-h5` 仍按旧 Vite H5 页面构建，容器根路径会继续返回 `index.html`，与“只做模型资源站”的部署目标冲突。
- 现有 Live2D 资源域没有任何服务端鉴权，匿名请求可直接命中 `/models/**` 静态文件。
- gateway 已经通过 Sa-Token 统一校验现有登录 token，最小改动是提供一个受该过滤器保护的内部探活式鉴权接口，供 nginx `auth_request` 复用，而不是新增第二套 token 机制。

### Fix
- 在 `medical-gateway` 新增 `/internal/live2d/auth-check` 本地端点；请求命中该端点时继续走现有 Sa-Token 登录校验，已登录返回 `204 No Content`，未登录维持 `401`。
- 将 `medical-mp/live2d-h5` Docker 镜像改为仅分发 `public/models` 静态目录，不再构建或发布旧 H5 页面。
- 重写 `live2d-h5` nginx 配置：`/models/**` 先经 `auth_request` 转发到 gateway 鉴权，根路径与其他路径统一返回 `404`，同时关闭目录浏览。
- 在 `docker-compose.yml` 中让 `live2d-h5` 显式依赖 `gateway` 启动，保持资源域和鉴权链路同网部署。

## Session: 2026-04-14 12:03

### Approved Scope
- 主人新增两个前端体验优化点：
  1. 删除 `medical-admin` 登录页默认自动填写的账号密码
  2. 小程序聊天页在数字人加载出来之前显示全屏 loading
- 主人明确选择了折中策略：loading 只等待数字人首次加载成功，不需要等待会话初始化和历史消息全部完成。
- 主人同时确认 admin 端只删除默认值，输入框保持空白，不额外改“记住上次登录内容”或退出清空策略。

### Planning
- 已写设计文档：`docs/superpowers/specs/2026-04-14-admin-login-cleanup-and-chat-loading-design.md`
- 已写实现计划：`docs/superpowers/plans/2026-04-14-admin-login-cleanup-and-chat-loading.md`
- 下一步按 coding-brain 路线派发单个前端 worker，处理 admin 登录页和小程序聊天页两个文件的改动。
