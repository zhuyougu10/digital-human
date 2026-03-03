# 13-api-testing.md — 接口测试计划

> **目标**: 为 medical-ai 后端 5 个微服务的 55 个 REST 端点编写 Controller 层接口测试（MockMvc / WebTestClient），确保请求路由、参数校验、权限控制、响应结构全部正确。
>
> **范围**: Controller 层集成测试（`@WebMvcTest` / `@WebFluxTest`），Mock Service 层。不测试真实数据库、Redis、Nacos 等外部依赖。
>
> **约定**: 测试文件统一放在各 service 的 `src/test/java/com/medical/<service>/controller/` 目录下。

---

## Task 0: 测试基础设施搭建（所有服务共用）

### Task 0.1: 父 POM 添加测试管理依赖

文件: `medical-ai/pom.xml`

在 `<dependencyManagement>` 中添加:
```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <version>2.2.224</version>
    <scope>test</scope>
</dependency>
```

在 `<build><pluginManagement><plugins>` 中添加:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.2.5</version>
    <configuration>
        <argLine>-Dfile.encoding=UTF-8</argLine>
    </configuration>
</plugin>
```

### Task 0.2: 创建公共测试基类

每个服务都需要 mock 掉 Sa-Token 鉴权 + Nacos + Redis。创建一个可复用的测试配置。

文件: `medical-ai/medical-common/medical-common-core/src/test/java/com/medical/common/core/test/BaseControllerTest.java`
- 这是一个工具类/文档，不实际作为基类（因为 @WebMvcTest 限制扫描范围）
- 提供每个服务测试类 copy 所需的 mock 注解清单

**核心 Mock 策略**:
```
1. @WebMvcTest(XxxController.class) — 只加载单个 Controller
2. @MockBean 对应的 Service 接口
3. @AutoConfigureMockMvc(addFilters = false) — 跳过 Sa-Token 等 Filter
4. @TestPropertySource 覆盖 Nacos/Redis/MySQL 配置使其不连接
5. @Import(XxxController.class) — 如需额外 Bean
```

### Task 0.3: 每个服务创建 application-test.yml

每个服务的 `src/test/resources/application-test.yml`:
```yaml
spring:
  cloud:
    nacos:
      discovery:
        enabled: false
      config:
        enabled: false
    loadbalancer:
      enabled: false
  datasource:
    url: jdbc:h2:mem:testdb;MODE=MYSQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  data:
    redis:
      host: localhost
      port: 6379

sa-token:
  token-name: Authorization
  token-prefix: Bearer

# 禁用 Knife4j
springdoc:
  api-docs:
    enabled: false
```

**注意**: `@WebMvcTest` 不加载 DataSource，所以这个配置主要是兜底。关键是 `@WebMvcTest` + `@MockBean` 的组合。

---

## Task 1: medical-user-service 接口测试（11 个端点）

文件: `medical-ai/medical-service/medical-user-service/src/test/java/com/medical/user/controller/AuthControllerTest.java`

### 1.1 AuthControllerTest（4 个端点，8 个测试用例）

| # | 测试方法 | 端点 | 验证内容 |
|---|----------|------|----------|
| 1 | `login_success` | POST /auth/login | 正确 LoginDTO → 200 + LoginVO(token, userInfo) |
| 2 | `login_invalidParam` | POST /auth/login | 空 username → 400 参数校验失败 |
| 3 | `login_wrongPassword` | POST /auth/login | Service 抛 BusinessException → 返回错误码 |
| 4 | `register_success` | POST /auth/register | 正确 RegisterDTO → 200 |
| 5 | `register_duplicateUsername` | POST /auth/register | Service 抛异常 → 返回错误码 |
| 6 | `wxLogin_success` | POST /auth/wx-login | 正确 WxLoginDTO → 200 + LoginVO |
| 7 | `logout_success` | POST /auth/logout | → 200 |
| 8 | `logout_noToken` | POST /auth/logout | 无 token → 仍 200（filter 已跳过） |

Mock 依赖: `@MockBean AuthService`, `@MockBean WxService`

文件: `medical-ai/medical-service/medical-user-service/src/test/java/com/medical/user/controller/SysUserControllerTest.java`

### 1.2 SysUserControllerTest（7 个端点，12 个测试用例）

| # | 测试方法 | 端点 | 验证内容 |
|---|----------|------|----------|
| 1 | `listUsers_success` | GET /user/list | PageQuery → 200 + PageResult<UserVO> 结构正确 |
| 2 | `listUsers_withKeyword` | GET /user/list?keyword=test | keyword 传递到 Service |
| 3 | `getUserInfo_success` | GET /user/info | → 200 + UserVO |
| 4 | `updateUserInfo_success` | PUT /user/info | UserUpdateDTO → 200 |
| 5 | `updateUserInfo_invalidParam` | PUT /user/info | 空 body → 400 |
| 6 | `toggleUserStatus_success` | PUT /user/{userId}/toggle-status | → 200 |
| 7 | `toggleUserStatus_notFound` | PUT /user/999/toggle-status | Service 抛异常 → 错误响应 |
| 8 | `assignRole_success` | POST /user/{userId}/role/DOCTOR | → 200 |
| 9 | `removeRole_success` | DELETE /user/{userId}/role/DOCTOR | → 200 |
| 10 | `innerGetUser_success` | GET /user/inner/{userId} | → 200 + UserInfoDTO |
| 11 | `innerGetUser_notFound` | GET /user/inner/999 | Service 返回 null → 错误响应 |
| 12 | `listUsers_pagination` | GET /user/list?pageNum=2&pageSize=5 | 验证分页参数传递 |

Mock 依赖: `@MockBean SysUserService`, `@MockBean AuthService`

---

## Task 2: medical-doctor-service 接口测试（21 个端点）

### 2.1 DoctorControllerTest（9 个端点，14 个测试用例）

文件: `medical-ai/medical-service/medical-doctor-service/src/test/java/com/medical/doctor/controller/DoctorControllerTest.java`

| # | 测试方法 | 端点 | 验证内容 |
|---|----------|------|----------|
| 1 | `listDoctors_success` | GET /doctor/list | → 200 + PageResult<DoctorVO> |
| 2 | `listDoctors_withDepartmentFilter` | GET /doctor/list?departmentId=1 | departmentId 正确传递 |
| 3 | `listDoctors_withKeyword` | GET /doctor/list?keyword=心脏 | keyword 正确传递 |
| 4 | `getDoctorById_success` | GET /doctor/1 | → 200 + DoctorVO |
| 5 | `getDoctorById_notFound` | GET /doctor/999 | → 错误响应 |
| 6 | `searchDoctors_success` | GET /doctor/search?keywords=头痛 | → 200 + List<DoctorVO> |
| 7 | `getMyProfile_success` | GET /doctor/my-profile | → 200 + DoctorVO |
| 8 | `updateMyProfile_success` | PUT /doctor/my-profile | DoctorProfileDTO → 200 |
| 9 | `createDoctor_success` | POST /doctor | DoctorProfileDTO → 200 |
| 10 | `updateDoctor_success` | PUT /doctor/1 | DoctorProfileDTO → 200 |
| 11 | `innerGetDoctor_success` | GET /doctor/inner/1 | → 200 + DoctorInfoDTO |
| 12 | `innerGetDoctor_notFound` | GET /doctor/inner/999 | → 错误响应 |
| 13 | `innerSearchBySymptom_success` | GET /doctor/inner/search?keywords=发烧 | → 200 + List<DoctorInfoDTO> |
| 14 | `createDoctor_invalidParam` | POST /doctor (空 body) | → 400 |

Mock 依赖: `@MockBean DoctorProfileService`

### 2.2 DepartmentControllerTest（6 个端点，10 个测试用例）

文件: `medical-ai/medical-service/medical-doctor-service/src/test/java/com/medical/doctor/controller/DepartmentControllerTest.java`

| # | 测试方法 | 端点 | 验证内容 |
|---|----------|------|----------|
| 1 | `listDepartments_success` | GET /department/list | → 200 + List<DepartmentVO> |
| 2 | `listDepartments_withKeyword` | GET /department/list?keyword=内科 | keyword 传递 |
| 3 | `getDepartmentById_success` | GET /department/1 | → 200 + DepartmentVO |
| 4 | `getDepartmentById_notFound` | GET /department/999 | → 错误响应 |
| 5 | `createDepartment_success` | POST /department | DepartmentDTO → 200 |
| 6 | `createDepartment_invalidParam` | POST /department (空 name) | → 400 |
| 7 | `updateDepartment_success` | PUT /department/1 | DepartmentDTO → 200 |
| 8 | `deleteDepartment_success` | DELETE /department/1 | → 200 |
| 9 | `toggleDepartmentStatus_success` | PUT /department/1/toggle-status | → 200 |
| 10 | `deleteDepartment_notFound` | DELETE /department/999 | → 错误响应 |

Mock 依赖: `@MockBean DepartmentService`

### 2.3 ScheduleControllerTest（9 个端点，13 个测试用例）

文件: `medical-ai/medical-service/medical-doctor-service/src/test/java/com/medical/doctor/controller/ScheduleControllerTest.java`

| # | 测试方法 | 端点 | 验证内容 |
|---|----------|------|----------|
| 1 | `getTemplates_success` | GET /schedule/template/1 | → 200 + List<ScheduleTemplate> |
| 2 | `createTemplate_success` | POST /schedule/template/1 | ScheduleTemplateDTO → 200 |
| 3 | `createTemplate_invalidParam` | POST /schedule/template/1 (空 body) | → 400 |
| 4 | `deleteTemplate_success` | DELETE /schedule/template/1 | → 200 |
| 5 | `getSlots_success` | GET /schedule/slots?doctorId=1&date=2026-03-10 | → 200 + List<ScheduleSlotVO> |
| 6 | `getSlots_missingParam` | GET /schedule/slots (无 doctorId) | → 400 |
| 7 | `getSlotsByDepartment_success` | GET /schedule/slots/department?departmentId=1&date=2026-03-10 | → 200 |
| 8 | `generateSlots_success` | POST /schedule/generate?startDate=2026-03-10&endDate=2026-03-17 | → 200 |
| 9 | `innerGetSlots_success` | GET /schedule/inner/slots?doctorId=1&date=2026-03-10 | → 200 + List<SlotInfoDTO> |
| 10 | `innerBookSlot_success` | POST /schedule/inner/slots/1/book | → 200 + true |
| 11 | `innerBookSlot_alreadyBooked` | POST /schedule/inner/slots/1/book | Service 返回 false → 200 + false |
| 12 | `innerCancelSlot_success` | POST /schedule/inner/slots/1/cancel | → 200 + true |
| 13 | `getSlots_invalidDateFormat` | GET /schedule/slots?doctorId=1&date=bad-date | → 400 |

Mock 依赖: `@MockBean ScheduleService`

---

## Task 3: medical-ai-service 接口测试（12 个端点）

### 3.1 ChatControllerTest（6 个端点，10 个测试用例）

文件: `medical-ai/medical-service/medical-ai-service/src/test/java/com/medical/ai/controller/ChatControllerTest.java`

**注意**: `/chat/send` 返回 `Flux<ServerSentEvent>`，需要使用 `WebTestClient` 而非 `MockMvc`。但因为 ai-service 同时引入了 web 和 webflux，`@WebMvcTest` 可能冲突。

**策略**: 对非 SSE 端点使用 `@WebMvcTest` + `MockMvc`；对 SSE 端点单独创建 `ChatSseControllerTest` 使用 `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `WebTestClient`，或简化为验证 Service 调用后 mock 返回 `Flux.empty()`。

| # | 测试方法 | 端点 | 验证内容 |
|---|----------|------|----------|
| 1 | `createSession_success` | POST /chat/session | CreateSessionDTO → 200 + ChatSessionVO |
| 2 | `createSession_invalidType` | POST /chat/session | 无效 sessionType → 400 或业务异常 |
| 3 | `listSessions_success` | GET /chat/sessions | → 200 + List<ChatSessionVO> |
| 4 | `getMessages_success` | GET /chat/session/1/messages | → 200 + List<ChatMessageVO> |
| 5 | `getMessages_notFound` | GET /chat/session/999/messages | → 错误响应 |
| 6 | `sendMessage_sseStream` | POST /chat/send | ChatRequestDTO → SSE 流（验证 Content-Type: text/event-stream） |
| 7 | `endSession_success` | POST /chat/session/1/end | → 200 |
| 8 | `deleteSession_success` | DELETE /chat/session/1 | → 200 |
| 9 | `deleteSession_notFound` | DELETE /chat/session/999 | → 错误响应 |
| 10 | `sendMessage_emptyContent` | POST /chat/send | 空 message → 400 |

Mock 依赖: `@MockBean ChatService`, `@MockBean SummaryService`, `@MockBean TtsService`

### 3.2 EncyclopediaControllerTest（4 个端点，6 个测试用例）

文件: `medical-ai/medical-service/medical-ai-service/src/test/java/com/medical/ai/controller/EncyclopediaControllerTest.java`

| # | 测试方法 | 端点 | 验证内容 |
|---|----------|------|----------|
| 1 | `createSession_success` | POST /encyclopedia/session | → 200 + ChatSessionVO |
| 2 | `listSessions_success` | GET /encyclopedia/sessions | → 200 + List<ChatSessionVO> |
| 3 | `getMessages_success` | GET /encyclopedia/session/1/messages | → 200 + List<ChatMessageVO> |
| 4 | `getMessages_notFound` | GET /encyclopedia/session/999/messages | → 错误响应 |
| 5 | `chat_sseStream` | POST /encyclopedia/chat | → SSE 流 |
| 6 | `chat_emptyMessage` | POST /encyclopedia/chat | 空 message → 400 |

Mock 依赖: `@MockBean ChatService`

### 3.3 SummaryControllerTest（2 个端点，4 个测试用例）

文件: `medical-ai/medical-service/medical-ai-service/src/test/java/com/medical/ai/controller/SummaryControllerTest.java`

| # | 测试方法 | 端点 | 验证内容 |
|---|----------|------|----------|
| 1 | `getBySession_success` | GET /summary/session/1 | → 200 + ConversationSummaryVO |
| 2 | `getBySession_notFound` | GET /summary/session/999 | → null 或 错误 |
| 3 | `getByAppointment_success` | GET /summary/appointment/1 | → 200 + ConversationSummaryVO |
| 4 | `getByAppointment_notFound` | GET /summary/appointment/999 | → null 或 错误 |

Mock 依赖: `@MockBean SummaryService`

---

## Task 4: medical-appointment-service 接口测试（8 个端点）

### 4.1 AppointmentControllerTest（8 个端点，14 个测试用例）

文件: `medical-ai/medical-service/medical-appointment-service/src/test/java/com/medical/appointment/controller/AppointmentControllerTest.java`

| # | 测试方法 | 端点 | 验证内容 |
|---|----------|------|----------|
| 1 | `createAppointment_success` | POST /appointment | CreateAppointmentDTO → 200 + Long(appointmentId) |
| 2 | `createAppointment_invalidParam` | POST /appointment | 缺少必填字段 → 400 |
| 3 | `getMyAppointments_success` | GET /appointment/my | PageQuery → 200 + PageResult<AppointmentListVO> |
| 4 | `getMyAppointments_pagination` | GET /appointment/my?pageNum=2&pageSize=5 | 分页参数验证 |
| 5 | `getDoctorAppointments_success` | GET /appointment/doctor | → 200 + List<AppointmentVO> |
| 6 | `getDoctorAppointments_withDate` | GET /appointment/doctor?date=2026-03-10 | date 参数传递 |
| 7 | `getAppointmentById_success` | GET /appointment/1 | → 200 + AppointmentVO |
| 8 | `getAppointmentById_notFound` | GET /appointment/999 | → 错误响应 |
| 9 | `cancelAppointment_success` | PUT /appointment/1/cancel | → 200 |
| 10 | `cancelAppointment_notFound` | PUT /appointment/999/cancel | → 错误响应 |
| 11 | `listAppointments_admin_success` | GET /appointment/list | AppointmentQueryDTO + PageQuery → 200 + PageResult |
| 12 | `getStatistics_success` | GET /appointment/statistics | → 200 + Map |
| 13 | `getStatistics_withDateRange` | GET /appointment/statistics?startDate=2026-03-01&endDate=2026-03-31 | 日期参数传递 |
| 14 | `innerCreate_success` | POST /appointment/inner/create?patientId=1&doctorId=1&slotId=1 | → 200 + Long |

Mock 依赖: `@MockBean AppointmentService`

---

## Task 5: medical-knowledge-service 接口测试（12 个端点）

### 5.1 KnowledgeBaseControllerTest（12 个端点，18 个测试用例）

文件: `medical-ai/medical-service/medical-knowledge-service/src/test/java/com/medical/knowledge/controller/KnowledgeBaseControllerTest.java`

| # | 测试方法 | 端点 | 验证内容 |
|---|----------|------|----------|
| 1 | `createKb_success` | POST /kb | KnowledgeBaseDTO → 200 + Long |
| 2 | `createKb_invalidParam` | POST /kb | 空 name → 400 |
| 3 | `listKb_success` | GET /kb/list | PageQuery → 200 + PageResult<KnowledgeBaseVO> |
| 4 | `listKb_pagination` | GET /kb/list?pageNum=2&pageSize=5 | 分页参数验证 |
| 5 | `getKbById_success` | GET /kb/1 | → 200 + KnowledgeBaseVO |
| 6 | `getKbById_notFound` | GET /kb/999 | → 错误响应 |
| 7 | `deleteKb_success` | DELETE /kb/1 | → 200 |
| 8 | `uploadDocument_success` | POST /kb/1/document (multipart) | MockMultipartFile → 200 + Long |
| 9 | `uploadDocument_noFile` | POST /kb/1/document (无文件) | → 400 |
| 10 | `listDocuments_success` | GET /kb/1/documents | → 200 + PageResult<KnowledgeDocumentVO> |
| 11 | `deleteDocument_success` | DELETE /kb/document/1 | → 200 |
| 12 | `listChunks_success` | GET /kb/document/1/chunks | → 200 + PageResult<KnowledgeChunkVO> |
| 13 | `createManualChunk_success` | POST /kb/1/chunk | ChunkManualDTO → 200 + Long |
| 14 | `createManualChunk_invalidParam` | POST /kb/1/chunk | 空 content → 400 |
| 15 | `deleteChunk_success` | DELETE /kb/chunk/1 | → 200 |
| 16 | `search_success` | POST /kb/search | KnowledgeSearchRequest → 200 + List<SearchResultVO> |
| 17 | `search_emptyQuery` | POST /kb/search | 空 query → 400 或空列表 |
| 18 | `innerSearch_success` | POST /kb/inner/search | KnowledgeSearchRequest → 200 + List<KnowledgeSearchResult> |

Mock 依赖: `@MockBean KnowledgeBaseService`, `@MockBean VectorStoreService`, `@MockBean EmbeddingService`, `@MockBean DocumentParseService`

---

## 测试用例总汇

| 服务 | 测试类 | 端点数 | 用例数 |
|------|--------|--------|--------|
| medical-user-service | AuthControllerTest + SysUserControllerTest | 11 | 20 |
| medical-doctor-service | DoctorControllerTest + DepartmentControllerTest + ScheduleControllerTest | 21 | 37 |
| medical-ai-service | ChatControllerTest + EncyclopediaControllerTest + SummaryControllerTest | 12 | 20 |
| medical-appointment-service | AppointmentControllerTest | 8 | 14 |
| medical-knowledge-service | KnowledgeBaseControllerTest | 12 | 18 |
| **合计** | **10 个测试类** | **64** | **109 个测试用例** |

> 注: 端点数含 inner 端点，部分端点衍生多个正向/反向测试用例。

---

## 执行顺序与依赖关系

```
Task 0 (基础设施)
    │
    ├─→ Task 1 (user-service)     ─── 无跨服务依赖
    ├─→ Task 2 (doctor-service)   ─── 无跨服务依赖
    ├─→ Task 3 (ai-service)       ─── 无跨服务依赖 (Feign 全部 MockBean)
    ├─→ Task 4 (appointment-service) ─ 无跨服务依赖
    └─→ Task 5 (knowledge-service)   ─ 无跨服务依赖
```

**所有 Task 1-5 可并行执行**（各服务独立，Controller 测试 Mock 掉所有 Service 层）。

---

## 验证标准

每个 Task 完成后执行:
```bash
mvn test -f medical-ai/pom.xml -pl medical-service/medical-<service> -am -DskipTests=false
```

**全量验证** (Task 0-5 全部完成后):
```bash
mvn test -f medical-ai/pom.xml
```

**通过标准**: 109 个测试用例全部 GREEN，0 failures, 0 errors。

---

## 注意事项

1. **Sa-Token Mock**: 使用 `@AutoConfigureMockMvc(addFilters = false)` 跳过安全过滤器，或使用 `@MockBean StpInterfaceImpl` + Mock `StpUtil`。推荐前者简化测试。
2. **SecurityUtil Mock**: 多个 Controller 用 `SecurityUtil.getUserId()` 获取当前用户。需要通过 `try (MockedStatic<SecurityUtil> mockSecurity = mockStatic(SecurityUtil.class))` 或自定义 TestConfiguration 模拟。
3. **SSE 端点**: `ChatController#send` 和 `EncyclopediaController#chat` 返回 `Flux<SSE>`。可简化测试为验证 Service 被调用 + Content-Type 为 `text/event-stream`。如 `@WebMvcTest` 不支持 reactive 返回值，改用 `@SpringBootTest` + `WebTestClient`。
4. **JSON 序列化**: 验证 `R<T>` 响应体结构 — `$.code`, `$.msg`, `$.data`。
5. **分页参数**: `PageQuery` 可能通过 `@ModelAttribute` 或 query params 绑定，测试中通过 `.param("pageNum", "1").param("pageSize", "10")` 传递。
6. **日期参数**: `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)` 格式为 `yyyy-MM-dd`。
7. **文件上传**: `KnowledgeBaseController#uploadDocument` 使用 `@RequestParam("file") MultipartFile`，测试中使用 `MockMvcRequestBuilders.multipart()` + `MockMultipartFile`。

---

## Commit 节点

- Task 0 完成后: `test(infra): 添加测试基础设施 (H2, surefire, application-test.yml)`
- Task 1-5 每个完成后: `test(<service>): 添加 <N> 个 Controller 接口测试用例`
- 全量通过后: `test: 109 个接口测试用例全部通过`
