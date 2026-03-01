# API 接口参考

## 1. 认证机制（Sa-Token + Gateway）

### 1.1 认证流程
1. 客户端调用登录接口 `POST /api/user/auth/login`
2. user-service 返回登录态 token（统一放在业务响应 `data.token`）
3. 客户端后续请求通过 Header 携带：
   - `Authorization: Bearer <token>`
4. Gateway 统一校验 token，校验通过后路由到对应微服务
5. 微服务内通过 Sa-Token 注解进行二次权限控制（如 `@SaCheckRole`）

### 1.2 关键配置
- `token-name: Authorization`
- `token-prefix: Bearer`
- `is-read-header: true`
- `is-read-cookie: false`

## 2. Gateway 路由总览（6 条）

| 路由ID | Path 规则 | 目标服务 | 说明 |
|---|---|---|---|
| `ai-service-sse` | `/api/ai/chat/send` | `medical-ai-service` | SSE 专用路由，超时单独配置 |
| `user-service` | `/api/user/**` | `medical-user-service` | 用户与认证 |
| `doctor-service` | `/api/doctor/**` | `medical-doctor-service` | 医生/科室/排班 |
| `ai-service` | `/api/ai/**` | `medical-ai-service` | AI 对话/摘要/百科 |
| `appointment-service` | `/api/appointment/**` | `medical-appointment-service` | 预约挂号 |
| `knowledge-service` | `/api/knowledge/**` | `medical-knowledge-service` | 知识库/RAG |

## 3. 用户服务 API（/api/user/**）

### 3.1 认证接口（AuthController，前缀 `/auth`）
| 方法 | 完整路径 | 说明 |
|---|---|---|
| POST | `/api/user/auth/login` | 用户登录 |
| POST | `/api/user/auth/register` | 用户注册 |
| POST | `/api/user/auth/wx-login` | 微信登录 |
| POST | `/api/user/auth/logout` | 用户登出 |

### 3.2 用户接口（SysUserController，前缀 `/user`）
| 方法 | 完整路径 | 说明 |
|---|---|---|
| GET | `/api/user/user/list` | 分页查询用户（管理员） |
| GET | `/api/user/user/info` | 当前登录用户信息 |
| PUT | `/api/user/user/info` | 更新当前用户信息 |
| PUT | `/api/user/user/{userId}/toggle-status` | 启用/禁用用户（管理员） |
| POST | `/api/user/user/{userId}/role/{roleKey}` | 分配角色（管理员） |
| DELETE | `/api/user/user/{userId}/role/{roleKey}` | 移除角色（管理员） |
| GET | `/api/user/user/inner/{userId}` | 内部调用：按 ID 查询用户 |

## 4. 医生服务 API（/api/doctor/**）

### 4.1 科室接口（DepartmentController，前缀 `/department`）
| 方法 | 完整路径 | 说明 |
|---|---|---|
| GET | `/api/doctor/department/list` | 科室列表 |
| GET | `/api/doctor/department/{id}` | 科室详情 |
| POST | `/api/doctor/department` | 创建科室（管理员） |
| PUT | `/api/doctor/department/{id}` | 更新科室（管理员） |
| DELETE | `/api/doctor/department/{id}` | 删除科室（管理员） |
| PUT | `/api/doctor/department/{id}/toggle-status` | 科室启停（管理员） |

### 4.2 医生接口（DoctorController，前缀 `/doctor`）
| 方法 | 完整路径 | 说明 |
|---|---|---|
| GET | `/api/doctor/doctor/list` | 医生分页列表（支持按科室过滤） |
| GET | `/api/doctor/doctor/{id}` | 医生详情 |
| GET | `/api/doctor/doctor/search` | 按症状关键词检索医生 |
| GET | `/api/doctor/doctor/my-profile` | 当前医生个人档案 |
| PUT | `/api/doctor/doctor/my-profile` | 更新个人档案（医生） |
| POST | `/api/doctor/doctor` | 新建医生档案（管理员） |
| PUT | `/api/doctor/doctor/{id}` | 更新医生档案（管理员） |
| GET | `/api/doctor/doctor/inner/{doctorId}` | 内部调用：医生信息 |
| GET | `/api/doctor/doctor/inner/search` | 内部调用：按症状检索医生 |

### 4.3 排班接口（ScheduleController，前缀 `/schedule`）
| 方法 | 完整路径 | 说明 |
|---|---|---|
| GET | `/api/doctor/schedule/template/{doctorId}` | 查询医生排班模板 |
| POST | `/api/doctor/schedule/template/{doctorId}` | 保存排班模板 |
| DELETE | `/api/doctor/schedule/template/{templateId}` | 删除排班模板 |
| GET | `/api/doctor/schedule/slots` | 查询医生可用号源 |
| GET | `/api/doctor/schedule/slots/department` | 按科室查询可用号源 |
| POST | `/api/doctor/schedule/generate` | 按时间段生成号源（管理员） |
| GET | `/api/doctor/schedule/inner/slots` | 内部调用：可用号源 |
| POST | `/api/doctor/schedule/inner/slots/{slotId}/book` | 内部调用：占用号源 |
| POST | `/api/doctor/schedule/inner/slots/{slotId}/cancel` | 内部调用：释放号源 |

## 5. AI 服务 API（/api/ai/**）

### 5.1 对话接口（ChatController，前缀 `/chat`）
| 方法 | 完整路径 | 说明 |
|---|---|---|
| POST | `/api/ai/chat/session` | 创建会话 |
| GET | `/api/ai/chat/sessions` | 会话列表 |
| GET | `/api/ai/chat/session/{sessionId}/messages` | 会话消息列表 |
| POST (SSE) | `/api/ai/chat/send` | 流式发送消息并接收 AI 回复 |
| POST | `/api/ai/chat/session/{sessionId}/end` | 结束会话 |
| DELETE | `/api/ai/chat/session/{sessionId}` | 删除会话 |

### 5.2 摘要接口（SummaryController，前缀 `/summary`）
| 方法 | 完整路径 | 说明 |
|---|---|---|
| GET | `/api/ai/summary/session/{sessionId}` | 按会话查询摘要 |
| GET | `/api/ai/summary/appointment/{appointmentId}` | 按预约查询摘要 |

### 5.3 百科接口（EncyclopediaController，前缀 `/encyclopedia`）
| 方法 | 完整路径 | 说明 |
|---|---|---|
| POST | `/api/ai/encyclopedia/session` | 创建百科会话 |
| GET | `/api/ai/encyclopedia/sessions` | 百科会话列表 |
| GET | `/api/ai/encyclopedia/session/{sessionId}/messages` | 百科消息列表 |
| POST (SSE) | `/api/ai/encyclopedia/chat` | 百科流式问答 |

## 6. 预约服务 API（/api/appointment/**）

（AppointmentController，前缀 `/appointment`）

| 方法 | 完整路径 | 说明 |
|---|---|---|
| POST | `/api/appointment/appointment` | 创建预约 |
| GET | `/api/appointment/appointment/my` | 我的预约（分页） |
| GET | `/api/appointment/appointment/doctor` | 医生预约列表 |
| GET | `/api/appointment/appointment/{id}` | 预约详情 |
| PUT | `/api/appointment/appointment/{id}/cancel` | 取消预约 |
| GET | `/api/appointment/appointment/list` | 全部预约（管理员） |
| GET | `/api/appointment/appointment/statistics` | 预约统计（管理员） |
| POST | `/api/appointment/appointment/inner/create` | 内部调用：创建预约 |

## 7. 知识库服务 API（/api/knowledge/**）

（KnowledgeBaseController，前缀 `/kb`）

| 方法 | 完整路径 | 说明 |
|---|---|---|
| POST | `/api/knowledge/kb` | 创建知识库（管理员） |
| GET | `/api/knowledge/kb/list` | 知识库分页列表（管理员） |
| GET | `/api/knowledge/kb/{id}` | 知识库详情 |
| DELETE | `/api/knowledge/kb/{id}` | 删除知识库（管理员） |
| POST | `/api/knowledge/kb/{kbId}/document` | 上传文档（管理员） |
| GET | `/api/knowledge/kb/{kbId}/documents` | 文档分页列表 |
| DELETE | `/api/knowledge/kb/document/{docId}` | 删除文档（管理员） |
| GET | `/api/knowledge/kb/document/{docId}/chunks` | 分块分页列表 |
| POST | `/api/knowledge/kb/{kbId}/chunk` | 手动新增分块（管理员） |
| DELETE | `/api/knowledge/kb/chunk/{chunkId}` | 删除分块（管理员） |
| POST | `/api/knowledge/kb/search` | 对外知识检索 |
| POST | `/api/knowledge/kb/inner/search` | 内部知识检索 |

## 8. 通用响应格式

统一使用 `R<T>` 返回：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {}
}
```

### 字段说明
- `code`: 业务状态码
- `msg`: 文本消息
- `data`: 业务数据载体

### 常用返回构造
- 成功：`R.ok()` / `R.ok(data)`
- 失败：`R.fail()` / `R.fail(code, msg)`

## 9. ErrorCode 枚举

| 分类 | 枚举 | 码值 | 含义 |
|---|---|---:|---|
| 通用 | `SUCCESS` | 200 | 操作成功 |
| 通用 | `FAIL` | 500 | 操作失败 |
| 通用 | `PARAM_ERROR` | 400 | 参数错误 |
| 通用 | `UNAUTHORIZED` | 401 | 未登录或登录过期 |
| 通用 | `FORBIDDEN` | 403 | 无权限访问 |
| 通用 | `NOT_FOUND` | 404 | 资源不存在 |
| 用户 | `USER_NOT_FOUND` | 1001 | 用户不存在 |
| 用户 | `USER_ALREADY_EXISTS` | 1002 | 用户已存在 |
| 用户 | `USER_PASSWORD_ERROR` | 1003 | 密码错误 |
| 用户 | `USER_DISABLED` | 1004 | 账户已禁用 |
| 用户 | `WX_LOGIN_FAIL` | 1005 | 微信登录失败 |
| 医生 | `DOCTOR_NOT_FOUND` | 2001 | 医生不存在 |
| 医生 | `DEPARTMENT_NOT_FOUND` | 2002 | 科室不存在 |
| 医生 | `SCHEDULE_CONFLICT` | 2003 | 排班冲突 |
| AI | `AI_SERVICE_ERROR` | 3001 | AI 服务异常 |
| AI | `AI_RATE_LIMIT` | 3002 | 请求过于频繁 |
| AI | `TTS_ERROR` | 3003 | 语音合成失败 |
| 预约 | `SLOT_NOT_AVAILABLE` | 4001 | 号源不可用 |
| 预约 | `APPOINTMENT_NOT_FOUND` | 4002 | 预约不存在 |
| 预约 | `APPOINTMENT_ALREADY_EXISTS` | 4003 | 重复预约 |
| 预约 | `APPOINTMENT_CANCEL_FAIL` | 4004 | 取消预约失败 |
| 知识库 | `KNOWLEDGE_BASE_NOT_FOUND` | 5001 | 知识库不存在 |
| 知识库 | `DOCUMENT_PARSE_ERROR` | 5002 | 文档解析失败 |
| 知识库 | `EMBEDDING_ERROR` | 5003 | 向量化处理失败 |

## 10. SSE 流式接口说明（`/api/ai/chat/send`）

### 10.1 请求
- Method: `POST`
- URL: `/api/ai/chat/send`
- Header:
  - `Authorization: Bearer <token>`
  - `Content-Type: application/json`
- Body（`ChatRequestDTO`）示例：

```json
{
  "sessionId": 1,
  "message": "我最近总是头痛，应该挂什么科？"
}
```

### 10.2 响应
- `Content-Type: text/event-stream`
- 服务端返回 `ServerSentEvent<SseMessageVO>` 流
- 每条事件包含：
  - `event`: 消息类型（由服务端 `msg.getType()` 填充）
  - `data`: `SseMessageVO` 数据体（流式 token/结果片段）

### 10.3 调用示例
```bash
curl -N -X POST http://localhost:8080/api/ai/chat/send \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"sessionId":1,"message":"你好"}'
```
