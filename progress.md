
## Session: 2026-03-03 (Phase 9 — API Controller Tests)

### Phase 9: 接口测试 (13-api-testing)
- **Status:** complete
- **Started:** 2026-03-03
- **Completed:** 2026-03-03
- Actions taken:
  - Task 0: 搭建测试基础设施 (添加 H2 依赖, BaseControllerTest, application-test.yml)
  - Task 1: 完成 `medical-user-service` 接口测试 (AuthControllerTest, SysUserControllerTest)
  - Task 2: 完成 `medical-doctor-service` 接口测试 (DoctorControllerTest, DepartmentControllerTest, ScheduleControllerTest)
  - Task 3: 完成 `medical-ai-service` 接口测试 (ChatControllerTest, EncyclopediaControllerTest, SummaryControllerTest)
  - Task 4: 完成 `medical-appointment-service` 接口测试 (AppointmentControllerTest)
  - Task 5: 完成 `medical-knowledge-service` 接口测试 (KnowledgeBaseControllerTest)
  - Task 6: 执行全量验证 `mvn test` — 109 个测试用例全部通过 (BUILD SUCCESS)
- Files created:
  - 10 个 Controller 测试类 (`*ControllerTest.java`)
  - 3 个测试专用 Application 类 (`Test*Application.java` 用于规避 `@MapperScan`/`@EnableFeignClients` 冲突)
  - 5 个 `application-test.yml`
- Files modified:
  - `GlobalExceptionHandler.java` — 增加 `HttpMessageNotReadableException`, `MissingServletRequestParameterException`, `MissingServletRequestPartException`, `MethodArgumentTypeMismatchException` 的异常处理 (返回 400 PARAM_ERROR)
- Errors encountered:
  - `AuthControllerTest`: 断言错误 (`ErrorCode.USER_ALREADY_EXISTS` 是 1002 而非 500) → 修正测试断言
  - `SysUserControllerTest`: `updateUserInfo_invalidParam` 返回 500 → `GlobalExceptionHandler` 增加 `HttpMessageNotReadableException` 处理
  - `ChatControllerTest`: `ApplicationContext` 启动失败 (`@MapperScan` 依赖缺失) → 创建 `TestAiApplication` (exclude DataSource/Redis) 并使用 `@ContextConfiguration`
  - `AppointmentControllerTest`: 同上 → 创建 `TestAppointmentApplication`
  - `KnowledgeBaseControllerTest`: 同上 → 创建 `TestKnowledgeApplication`
  - `KnowledgeBaseControllerTest`: `uploadDocument_noFile` 返回 500 → `GlobalExceptionHandler` 增加 `MissingServletRequestPartException` 处理
