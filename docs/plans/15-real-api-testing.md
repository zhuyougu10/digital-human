# 15-real-api-testing: 真实接口集成测试 (Python)

> 目标：创建独立 Python 测试项目 `tests/`，使用 pytest + requests 通过 Gateway 对全部公开 API 进行端到端真实调用测试，验证完整请求链路（Gateway -> Service -> DB -> Response）。
> 前提：Docker Compose 全栈已启动（14 containers UP）。

## 技术选型

| 项 | 选择 | 理由 |
|----|------|------|
| HTTP 客户端 | **requests 2.31+** | Python 生态标准、简洁直观 |
| 测试框架 | **pytest 8.x** + pytest-ordering | 控制执行顺序、丰富断言、fixture 机制 |
| SSE 客户端 | **sseclient-py** | 解析 Server-Sent Events 流 |
| 配置 | `tests/config.py` | Gateway URL、admin 账号等集中管理 |
| 报告 | pytest-html (可选) | 生成 HTML 报告 |

## 目录结构

```
tests/                                  <- 项目根目录下新建
├── requirements.txt                    <- pytest, requests, sseclient-py
├── conftest.py                         <- pytest fixtures: tokens, shared state
├── config.py                           <- BASE_URL, credentials
├── test_01_auth.py                     <- Task 1: 认证
├── test_02_user.py                     <- Task 2: 用户管理
├── test_03_department.py               <- Task 3: 科室管理
├── test_04_doctor.py                   <- Task 4: 医生管理
├── test_05_schedule.py                 <- Task 5: 排班管理
├── test_06_knowledge.py                <- Task 6: 知识库管理
├── test_07_appointment.py              <- Task 7: 预约管理
├── test_08_chat.py                     <- Task 8: AI 对话
├── test_09_e2e_flow.py                 <- Task 9: 全链路
└── test-upload.txt                     <- 文档上传测试文件
```

## 前置条件

- Docker Compose 14 containers 全部 UP（`docker compose ps` 验证）
- Gateway 可达：`http://localhost:8080`
- MySQL init.sql 已执行（admin 用户 + 10 个科室已存在）
- 种子数据：admin/admin123 (ADMIN 角色)
- Python 3.10+

## Gateway 路由映射（StripPrefix=2）

| 外部路径 | 内部路径 | 服务 |
|---------|---------|------|
| `/api/user/**` | `/**` | medical-user-service |
| `/api/doctor/**` | `/**` | medical-doctor-service |
| `/api/ai/**` | `/**` | medical-ai-service |
| `/api/appointment/**` | `/**` | medical-appointment-service |
| `/api/knowledge/**` | `/**` | medical-knowledge-service |

## 公共基础设施

### config.py
```python
BASE_URL = "http://localhost:9090/api"
ADMIN_USERNAME = "admin"
ADMIN_PASSWORD = "admin123"
TEST_PASSWORD = "Test123456"
```

### conftest.py — pytest fixtures (session 级别)
```python
import pytest, requests, time
from config import *

class SharedState:
    """跨测试模块共享状态"""
    admin_token = None
    patient_token = None
    doctor_token = None
    patient_username = None
    doctor_username = None
    doctor_user_id = None
    doctor_profile_id = None
    # ... 更多共享 ID

@pytest.fixture(scope="session")
def state():
    return SharedState()

@pytest.fixture(scope="session", autouse=True)
def admin_login(state):
    """session 开始时自动登录 admin"""
    r = requests.post(f"{BASE_URL}/user/auth/login",
                      json={"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD})
    assert r.status_code == 200
    data = r.json()
    assert data["code"] == 200
    state.admin_token = data["data"]["token"]
    return state.admin_token

def auth_header(token):
    return {"Authorization": f"Bearer {token}"}
```

---

## Task 0: 项目骨架搭建

**文件清单：**
1. `tests/requirements.txt`:
   ```
   pytest>=8.0
   requests>=2.31
   sseclient-py>=1.8
   pytest-html>=4.0
   ```
2. `tests/config.py` — 配置常量
3. `tests/conftest.py` — fixtures + SharedState
4. `tests/test-upload.txt` — "这是一份关于高血压防治的医学知识文档。高血压是最常见的慢性病之一..."
5. 安装依赖: `pip install -r tests/requirements.txt`

**验证标准：** `cd tests && pytest --co` 能发现所有测试（collect-only）。

---

## Task 1: test_01_auth.py — 认证接口 (7 用例)

| # | 用例函数名 | 方法 | 路径 | 预期 |
|---|-----------|------|------|------|
| 1 | `test_admin_login` | POST | `/user/auth/login` | code=200, `data.token` 非空 |
| 2 | `test_login_wrong_password` | POST | `/user/auth/login` | code != 200 |
| 3 | `test_register_patient` | POST | `/user/auth/register` | code=200, 存 state.patient_username |
| 4 | `test_register_duplicate` | POST | `/user/auth/register` | code != 200 |
| 5 | `test_patient_login` | POST | `/user/auth/login` | token 非空, 存 state.patient_token |
| 6 | `test_get_current_user_info` | GET | `/user/user/info` | data.username 匹配 |
| 7 | `test_logout` | POST | `/user/auth/logout` | code=200 |

**额外:** 注册 `test_doctor_{timestamp}` 用户，登录获取其 userId，存入 state。

---

## Task 2: test_02_user.py — 用户管理 (6 用例, ADMIN token)

| # | 用例函数名 | 方法 | 路径 | 预期 |
|---|-----------|------|------|------|
| 1 | `test_user_list_paginated` | GET | `/user/user/list?pageNum=1&pageSize=10` | records 非空 |
| 2 | `test_user_list_search` | GET | `/user/user/list?keyword=admin` | records 含 admin |
| 3 | `test_assign_doctor_role` | POST | `/user/user/{userId}/role/DOCTOR` | code=200 |
| 4 | `test_disable_user` | PUT | `/user/user/{userId}/toggle-status` | code=200 |
| 5 | `test_enable_user` | PUT | `/user/user/{userId}/toggle-status` | code=200 |
| 6 | `test_remove_role` | DELETE | `/user/user/{userId}/role/DOCTOR` | code=200 |

**结尾:** 重新赋 DOCTOR 角色并让 doctor 用户登录获取 DOCTOR_TOKEN。

---

## Task 3: test_03_department.py — 科室管理 (7 用例, ADMIN token)

| # | 用例函数名 | 方法 | 路径 | 预期 |
|---|-----------|------|------|------|
| 1 | `test_list_departments` | GET | `/doctor/department/list` | data 长度 >= 10 |
| 2 | `test_create_department` | POST | `/doctor/department` | 返回新 ID |
| 3 | `test_get_department` | GET | `/doctor/department/{id}` | name 匹配 |
| 4 | `test_update_department` | PUT | `/doctor/department/{id}` | code=200 |
| 5 | `test_disable_department` | PUT | `/doctor/department/{id}/toggle-status` | code=200 |
| 6 | `test_enable_department` | PUT | `/doctor/department/{id}/toggle-status` | code=200 |
| 7 | `test_delete_department` | DELETE | `/doctor/department/{id}` | code=200 |

---

## Task 4: test_04_doctor.py — 医生管理 (8 用例)

| # | 用例函数名 | Auth | 方法 | 路径 | 预期 |
|---|-----------|------|------|------|------|
| 1 | `test_create_doctor` | ADMIN | POST | `/doctor/doctor` | 返回 doctor ID |
| 2 | `test_list_doctors` | 无 | GET | `/doctor/doctor/list` | records 非空 |
| 3 | `test_list_by_department` | 无 | GET | `/doctor/doctor/list?departmentId=1` | 200 |
| 4 | `test_get_doctor` | 无 | GET | `/doctor/doctor/{id}` | name 匹配 |
| 5 | `test_admin_update_doctor` | ADMIN | PUT | `/doctor/doctor/{id}` | 200 |
| 6 | `test_doctor_view_profile` | DOCTOR | GET | `/doctor/doctor/my-profile` | 200 |
| 7 | `test_doctor_update_profile` | DOCTOR | PUT | `/doctor/doctor/my-profile` | 200 |
| 8 | `test_search_by_symptom` | 无 | GET | `/doctor/doctor/search?keywords=头痛` | 200 |

---

## Task 5: test_05_schedule.py — 排班管理 (7 用例)

| # | 用例函数名 | Auth | 方法 | 路径 | 预期 |
|---|-----------|------|------|------|------|
| 1 | `test_create_template` | 无 | POST | `/doctor/schedule/template/{doctorId}` | 200 |
| 2 | `test_get_templates` | 无 | GET | `/doctor/schedule/template/{doctorId}` | 非空 |
| 3 | `test_generate_slots` | ADMIN | POST | `/doctor/schedule/generate?startDate=...&endDate=...` | 200 |
| 4 | `test_get_slots_by_doctor` | 无 | GET | `/doctor/schedule/slots?doctorId=X&date=tomorrow` | 200 |
| 5 | `test_get_slots_by_department` | 无 | GET | `/doctor/schedule/slots/department?departmentId=1&date=tomorrow` | 200 |
| 6 | `test_delete_template` | 无 | DELETE | `/doctor/schedule/template/{templateId}` | 200 |
| 7 | `test_template_deleted` | 无 | GET | `/doctor/schedule/template/{doctorId}` | 空数组 |

---

## Task 6: test_06_knowledge.py — 知识库管理 (10 用例, ADMIN token)

| # | 用例函数名 | 方法 | 路径 | 预期 |
|---|-----------|------|------|------|
| 1 | `test_create_kb` | POST | `/knowledge/kb` | 返回 ID |
| 2 | `test_list_kb` | GET | `/knowledge/kb/list` | records 含新建 KB |
| 3 | `test_get_kb` | GET | `/knowledge/kb/{id}` | name 匹配 |
| 4 | `test_upload_document` | POST multipart | `/knowledge/kb/{kbId}/document` | 返回 doc ID |
| 5 | `test_list_documents` | GET | `/knowledge/kb/{kbId}/documents` | records 非空 |
| 6 | `test_add_manual_chunk` | POST | `/knowledge/kb/{kbId}/chunk` | 返回 chunk ID |
| 7 | `test_list_chunks` | GET | `/knowledge/kb/document/{docId}/chunks` | 200 |
| 8 | `test_search_kb` | POST | `/knowledge/kb/search` | 200 (允许空) |
| 9 | `test_delete_chunk` | DELETE | `/knowledge/kb/chunk/{chunkId}` | 200 |
| 10 | `test_delete_kb` | DELETE | `/knowledge/kb/{id}` | 200 |

---

## Task 7: test_07_appointment.py — 预约管理 (8 用例)

| # | 用例函数名 | Auth | 方法 | 路径 | 预期 |
|---|-----------|------|------|------|------|
| 1 | `test_create_appointment` | PATIENT | POST | `/appointment/appointment` | 返回 ID |
| 2 | `test_my_appointments` | PATIENT | GET | `/appointment/appointment/my` | records 非空 |
| 3 | `test_appointment_detail` | PATIENT | GET | `/appointment/appointment/{id}` | doctorId 匹配 |
| 4 | `test_doctor_appointments` | DOCTOR | GET | `/appointment/appointment/doctor` | 200 |
| 5 | `test_admin_list` | ADMIN | GET | `/appointment/appointment/list` | records 非空 |
| 6 | `test_admin_statistics` | ADMIN | GET | `/appointment/appointment/statistics` | data 非空 |
| 7 | `test_cancel_appointment` | PATIENT | PUT | `/appointment/appointment/{id}/cancel` | 200 |
| 8 | `test_verify_cancelled` | PATIENT | GET | `/appointment/appointment/{id}` | status 已取消 |

**前置:** 先通过 GET `/doctor/schedule/slots` 获取可用 slotId。

---

## Task 8: test_08_chat.py — AI 对话 (6 用例)

| # | 用例函数名 | Auth | 方法 | 路径 | 预期 |
|---|-----------|------|------|------|------|
| 1 | `test_create_session` | PATIENT | POST | `/ai/chat/session` | sessionType=TRIAGE |
| 2 | `test_list_sessions` | PATIENT | GET | `/ai/chat/sessions` | 包含新会话 |
| 3 | `test_sse_send_message` | PATIENT | POST | `/ai/chat/send` | Content-Type 含 text/event-stream |
| 4 | `test_message_history` | 无 | GET | `/ai/chat/session/{id}/messages` | 200 |
| 5 | `test_end_session` | 无 | POST | `/ai/chat/session/{id}/end` | 200 |
| 6 | `test_delete_session` | 无 | DELETE | `/ai/chat/session/{id}` | 200 |

**SSE 测试策略：** 使用 `requests.post(..., stream=True)` + `sseclient.SSEClient`，验证响应头含 `text/event-stream`，读取若干事件后关闭。允许 LLM 错误事件。

---

## Task 9: test_09_e2e_flow.py — 核心业务全链路 (1 用例, 9 步)

```python
def test_full_patient_journey(state):
    """患者完整就诊流程: 登录 → 咨询 → 查医生 → 预约 → 取消"""
    # 1. 患者登录
    # 2. 创建 TRIAGE 会话
    # 3. 发送症状描述 (SSE, 验证连接)
    # 4. 查询医生列表
    # 5. 查看医生详情
    # 6. 查询号源
    # 7. 创建预约
    # 8. 查看预约详情
    # 9. 取消预约
```

---

## 执行方式

```bash
# 1. 安装依赖
cd tests && pip install -r requirements.txt

# 2. 运行全部测试 (需 Docker Compose 已启动)
pytest -v --tb=short

# 3. 运行单个模块
pytest test_01_auth.py -v

# 4. 生成 HTML 报告
pytest -v --html=report.html --self-contained-html
```

## 测试数据管理策略

| 策略 | 说明 |
|------|------|
| 自包含 | 通过 conftest.py fixtures 管理生命周期 |
| 时间戳隔离 | 用户名/知识库名包含 `int(time.time())` 时间戳 |
| 清理可选 | CRUD 测试最后执行 delete，但不依赖清理成功 |
| 种子数据依赖 | 仅依赖 init.sql 的 admin 用户 + 10 个科室 |

## 预期用例总数

| Task | 文件 | 用例数 |
|------|------|--------|
| 0 | conftest.py (骨架) | 0 |
| 1 | test_01_auth.py | 7 |
| 2 | test_02_user.py | 6 |
| 3 | test_03_department.py | 7 |
| 4 | test_04_doctor.py | 8 |
| 5 | test_05_schedule.py | 7 |
| 6 | test_06_knowledge.py | 10 |
| 7 | test_07_appointment.py | 8 |
| 8 | test_08_chat.py | 6 |
| 9 | test_09_e2e_flow.py | 1 (含 9 步) |
| **合计** | **11 文件** | **60 用例** |

## 约束

- 所有请求通过 Gateway (`http://localhost:8080/api/...`)，不直连微服务
- SSE 端点测试仅验证连接建立和响应头，不依赖 LLM API Key
- 知识库搜索允许空结果（依赖 DashScope embedding API）
- 不修改任何现有业务代码
- Python 3.10+，UTF-8 编码
