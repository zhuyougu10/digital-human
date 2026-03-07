# AGENTS.md — AI 数字人医疗小助手系统

Repository-wide instructions for AI coding agents. Read this before touching any code.

---

## Project Layout

```
medical-ai/          # Java 17 Spring Cloud microservices (Maven multi-module)
medical-admin/       # Admin/Doctor web UI (Vue 3 + Vite, plain JavaScript)
medical-mp/          # Patient mini-program (UniApp + TypeScript + Vite)
tests/               # Python integration test suite (pytest)
findings.md          # Architecture decisions & API contracts — READ FIRST
task_plan.md         # Task checklist ([ ] unchecked, [x] done)
progress.md          # Bug/fix log — record every root-cause finding here
```

---

## Build Commands

### Java Back-end (`medical-ai/`)

```bash
# Build all modules, skip tests
mvn clean package -DskipTests -f medical-ai/pom.xml

# Build a single module
mvn clean package -DskipTests -pl medical-service/medical-ai-service -am -f medical-ai/pom.xml

# Run ALL Java unit tests
mvn test -f medical-ai/pom.xml

# Run tests for a single module
mvn test -pl medical-service/medical-ai-service -f medical-ai/pom.xml

# Run a single test class
mvn test -pl medical-service/medical-ai-service -f medical-ai/pom.xml \
  -Dtest=ChatControllerTest

# Run a single test method
mvn test -pl medical-service/medical-ai-service -f medical-ai/pom.xml \
  -Dtest=ChatControllerTest#testCreateSession
```

### Vue 3 Admin Front-end (`medical-admin/`)

```bash
npm install --prefix medical-admin
npm run dev   --prefix medical-admin   # dev server on :5173, proxy → :8080
npm run build --prefix medical-admin   # output: medical-admin/dist/
```

### UniApp Mini-program (`medical-mp/`)

```bash
npm install --prefix medical-mp
npm run dev:mp-weixin   --prefix medical-mp   # H5 preview
npm run build:mp-weixin --prefix medical-mp   # WeChat MP build
```

### Python Integration Tests (`tests/`)

```bash
# Prerequisites: gateway running on localhost:8080 (or 9090)
pip install pytest requests sseclient-py pytest-html

# Run all integration tests
pytest tests/ -v

# Run a single test file
pytest tests/test_08_chat.py -v

# Run a single test function
pytest tests/test_01_auth.py::test_login_success -v

# Generate HTML report
pytest tests/ --html=report.html --self-contained-html
```

### Docker Infrastructure

```bash
# Start all infrastructure + services (from medical-ai/docker/)
docker compose -f medical-ai/docker/docker-compose.yml up -d

# Tail a specific service log
docker compose -f medical-ai/docker/docker-compose.yml logs -f ai-service
```

---

## Java Code Style (Spring Boot 3 / Java 17)

### Package Structure

`com.medical.<service>.<layer>` — e.g. `com.medical.ai.service.impl`

Layers in order: `controller → service → service.impl → domain → mapper`

### Naming Conventions

| Artifact | Convention | Example |
|---|---|---|
| Entity | No suffix | `ChatSession`, `SysUser` |
| DTO (request body) | `*DTO` | `CreateSessionDTO` |
| VO (response) | `*VO` | `ChatSessionVO`, `SseMessageVO` |
| Mapper | `*Mapper` extends `BaseMapper<E>` | `ChatSessionMapper` |
| Service interface | `*Service` | `ChatService` |
| Service impl | `*ServiceImpl` | `ChatServiceImpl` |
| Agent (AI tool) | `*Agent` implements `Agent` | `AppointmentAgent` |
| Error codes | `ErrorCode` enum, grouped by module | `1xxx`=user, `2xxx`=doctor, `3xxx`=AI |

### Response Wrapper

Always return `R<T>` from controllers. Use static factories only:

```java
return R.ok();           // 200, no data
return R.ok(data);       // 200 + payload
return R.fail(msg);      // business error
return R.fail(ErrorCode.AI_SERVICE_UNAVAILABLE);
```

### Error Handling

Throw `BusinessException` for all domain errors — never `RuntimeException` directly:

```java
throw new BusinessException(ErrorCode.USER_NOT_FOUND);
```

`GlobalExceptionHandler` (`@RestControllerAdvice`) catches it globally. Never swallow exceptions silently; always log at `log.error(...)` level with context before re-throwing or returning a fail response.

### Entity Conventions

All entities extend `BaseEntity` (auto-filled audit fields: `createTime`, `createBy`, `updateTime`, `updateBy`, `deleted` with `@TableLogic`). Never set audit fields manually.

```java
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_session")
public class ChatSession extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    // camelCase fields → underscore columns via MyBatis-Plus global config
}
```

### Controller Pattern

```java
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor   // constructor injection via Lombok, never @Autowired
public class ChatController {
    private final ChatService chatService;

    @PostMapping("/session")
    public R<ChatSessionVO> createSession(@RequestBody @Validated CreateSessionDTO dto) {
        Long userId = SecurityUtil.getUserId();   // static auth context (Sa-Token)
        return R.ok(chatService.createSession(userId, dto));
    }
}
```

### SSE / Reactive

`ChatService.chat()` returns `Flux<SseMessageVO>`. Always add `.publishOn(Schedulers.boundedElastic())` immediately after `chatModel.stream(prompt)` to move blocking tool calls off the Netty I/O thread. Return `text/event-stream` via `produces` on the `@GetMapping`.

### Imports

Use constructor injection everywhere (`@RequiredArgsConstructor`). Use `LambdaQueryWrapper<T>` for MyBatis-Plus queries — never string-based column names. Import only what is used; avoid wildcard imports.

---

## Vue 3 Front-end Code Style (`medical-admin/`, plain JavaScript)

### File & Component Naming

- Views: `PascalCase.vue` (`UserManagement.vue`)
- Components: `PascalCase.vue` (`ChatPanel.vue`)
- API modules: `camelCase.js` (`userApi.js`, `doctorApi.js`)
- Pinia stores: `use<Name>Store` composition-style (not Options API)

### API Layer Pattern

Every API call goes through the shared Axios instance in `src/api/request.js`. Define per-domain functions in `src/api/<domain>.js`:

```js
import service from './request'
export const getUserList = (params) => service.get('/user/list', { params })
export const toggleUserStatus = (id, status) => service.put(`/user/${id}/status`, { status })
```

The interceptor automatically injects `Authorization: Bearer <token>` and unwraps `R<T>`.

### Component Style

Use `<script setup>` (Composition API). Keep template logic minimal — extract computed properties. Emit events upward; do not mutate props. Use Element Plus components; match existing patterns in neighbouring views before introducing new UI patterns.

---

## UniApp Mini-program Code Style (`medical-mp/`, TypeScript)

- Use `uni.request()` wrapper, never fetch/Axios.
- Auth token: `uni.getStorageSync('token')`.
- Navigation: `uni.reLaunch()` for auth changes, `uni.navigateTo()` otherwise.
- Error feedback: `uni.showToast({ title, icon: 'none' })`.
- SSE streaming: use the custom `src/utils/sse.js` utility (native EventSource is unsupported in mini-programs).

---

## Python Test Style (`tests/`)

- Type-annotate all function signatures (`str | None`, `dict[str, Any]`).
- Check responses with `assert_success(payload)` (verifies `code == 200`).
- Use the `SharedState` dataclass (session-scoped fixture) to pass data between test files — never use global variables.
- Test file order matters (`test_01_*` → `test_09_*`); later files depend on state set by earlier ones.
- One test function per behaviour; no `parametrize` unless testing a tight data set.

---

## Critical Architectural Constraints

1. **Sa-Token `token-name`** must be identical across the gateway and every service config — it doubles as the HTTP header name and Redis key prefix.
2. **Milvus SDK** — exclude `com.squareup.okhttp3:okhttp` transitive dependency in any module that depends on `milvus-sdk-java` to prevent Kotlin classpath conflicts.
3. **Tika + Milvus** — both must exclude `org.eclipse.jetty:jetty-client` to prevent Jetty overriding Spring's HTTP client.
4. **Spring AI M5** — inject `FunctionCallbackResolver` manually into `OpenAiChatModel` constructor; do not rely on auto-configuration for tool registration.
5. **Five independent databases** — no cross-service transactions. Inter-service data access goes through Feign clients defined in `medical-api/`.
6. **H2 test profile** — all controller tests use `@ActiveProfiles("test")`, `TestAiApplication` (excludes DataSource/Redis auto-config), and `application-test.yml` with H2 in MySQL mode. Never let test code depend on a running MySQL or Redis.
