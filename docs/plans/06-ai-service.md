# 06 - AI 服务 (ai-service) -- 系统核心

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现对话管理、4 个 AI Agent（导诊/医疗问答/对话摘要/医生百科）、RAG 检索编排、TTS 语音合成调用、SSE 流式响应。这是系统最核心的模块。

**Architecture:** 基于 Spring AI 的 ChatClient + Function Calling 实现 Agent 模式。每个 Agent 有独立的 System Prompt + 工具集。对话通过 SSE 流式返回前端，同时异步调用阿里云 TTS 生成语音。RAG 通过 Feign 调用 knowledge-service 的语义检索接口。

**Tech Stack:** Spring AI 1.0+ (DeepSeek/通义千问), Spring WebFlux (SSE), OpenFeign, 阿里云 TTS SDK

**前置依赖:** `03-user-service` + `04-doctor-service` + `05-knowledge-service` 完成

---

## Task 1: POM 依赖补充

**Files:**
- Modify: `medical-ai/medical-service/medical-ai-service/pom.xml`

```xml
<!-- Spring AI - OpenAI 兼容（DeepSeek/通义千问均兼容 OpenAI 协议） -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
</dependency>
<!-- WebFlux (SSE 支持) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
<!-- Feign 调用其他服务 -->
<dependency>
    <groupId>com.medical</groupId>
    <artifactId>medical-doctor-api</artifactId>
</dependency>
<dependency>
    <groupId>com.medical</groupId>
    <artifactId>medical-appointment-api</artifactId>
</dependency>
<dependency>
    <groupId>com.medical</groupId>
    <artifactId>medical-knowledge-api</artifactId>
</dependency>
<dependency>
    <groupId>com.medical</groupId>
    <artifactId>medical-user-api</artifactId>
</dependency>
<!-- 阿里云 NLS SDK (TTS) -->
<dependency>
    <groupId>com.alibaba.nls</groupId>
    <artifactId>nls-sdk-tts</artifactId>
    <version>${aliyun-sdk-nls.version}</version>
</dependency>
```

---

## Task 2: 数据库表设计

**Files:**
- Create: `medical-ai/medical-service/medical-ai-service/src/main/resources/db/V1__init_ai_tables.sql`

```sql
-- 对话会话表
CREATE TABLE IF NOT EXISTS `chat_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `session_type` VARCHAR(32) NOT NULL COMMENT '会话类型: TRIAGE/QA/ENCYCLOPEDIA',
    `title` VARCHAR(128) DEFAULT '新对话' COMMENT '会话标题',
    `agent_type` VARCHAR(32) DEFAULT NULL COMMENT '当前 Agent 类型',
    `status` TINYINT DEFAULT 0 COMMENT '0进行中 1已结束 2已总结',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话会话表';

-- 对话消息表
CREATE TABLE IF NOT EXISTS `chat_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `session_id` BIGINT NOT NULL COMMENT '会话ID',
    `role` VARCHAR(16) NOT NULL COMMENT 'user/assistant/system/tool',
    `content` TEXT COMMENT '消息内容',
    `tool_call_id` VARCHAR(64) DEFAULT NULL COMMENT 'tool call ID',
    `tool_name` VARCHAR(64) DEFAULT NULL COMMENT '工具名称',
    `metadata` JSON DEFAULT NULL COMMENT '元数据(卡片类型、医生推荐列表等)',
    `tts_url` VARCHAR(512) DEFAULT NULL COMMENT 'TTS音频URL',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话消息表';

-- 对话总结表
CREATE TABLE IF NOT EXISTS `conversation_summary` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `session_id` BIGINT NOT NULL COMMENT '会话ID',
    `user_id` BIGINT NOT NULL COMMENT '患者ID',
    `appointment_id` BIGINT DEFAULT NULL COMMENT '关联预约ID',
    `chief_complaint` VARCHAR(500) DEFAULT NULL COMMENT '主诉',
    `symptoms` VARCHAR(500) DEFAULT NULL COMMENT '伴随症状',
    `duration` VARCHAR(128) DEFAULT NULL COMMENT '持续时间',
    `severity` VARCHAR(64) DEFAULT NULL COMMENT '严重程度',
    `medical_history` VARCHAR(500) DEFAULT NULL COMMENT '既往史',
    `ai_assessment` VARCHAR(500) DEFAULT NULL COMMENT 'AI初步判断',
    `full_summary` TEXT COMMENT '完整摘要文本',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_appointment_id` (`appointment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话总结表';
```

---

## Task 3: Entity + Mapper + DTO/VO

Entity: `ChatSession`, `ChatMessage`, `ConversationSummary`
Mapper: 各自 BaseMapper
DTO:
- `ChatRequestDTO(sessionId, message, sessionType)` - 用户发送消息
- `CreateSessionDTO(sessionType)` - 创建会话
VO:
- `ChatSessionVO` - 会话列表
- `ChatMessageVO` - 消息（含 metadata JSON 解析为 Map）
- `ConversationSummaryVO` - 对话摘要
- `SseMessageVO(type, content, metadata, ttsUrl)` - SSE 推送的消息结构

---

## Task 4: Spring AI 配置 - 多模型支持

**Files:**
- Create: `com/medical/ai/config/AiModelConfig.java`

```java
package com.medical.ai.config;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiModelConfig {

    /** DeepSeek - 主力模型 (对话/推理) */
    @Bean
    @Primary
    public OpenAiChatModel deepSeekChatModel(
            @Value("${ai.deepseek.api-key}") String apiKey,
            @Value("${ai.deepseek.base-url}") String baseUrl,
            @Value("${ai.deepseek.model}") String model) {
        OpenAiApi api = new OpenAiApi(baseUrl, apiKey);
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(model)
                .withTemperature(0.7)
                .withMaxTokens(2048)
                .build();
        return new OpenAiChatModel(api, options);
    }

    /** 通义千问 - 备选模型 */
    @Bean("qwenChatModel")
    public OpenAiChatModel qwenChatModel(
            @Value("${ai.qwen.api-key}") String apiKey,
            @Value("${ai.qwen.base-url}") String baseUrl,
            @Value("${ai.qwen.model}") String model) {
        OpenAiApi api = new OpenAiApi(baseUrl, apiKey);
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(model)
                .withTemperature(0.7)
                .withMaxTokens(2048)
                .build();
        return new OpenAiChatModel(api, options);
    }
}
```

application.yml：
```yaml
ai:
  deepseek:
    api-key: ${DEEPSEEK_API_KEY:your-key}
    base-url: https://api.deepseek.com
    model: deepseek-chat
  qwen:
    api-key: ${DASHSCOPE_API_KEY:your-key}
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    model: qwen-plus
```

---

## Task 5: Agent Function Calling - 工具定义

**Files:**
- Create: `com/medical/ai/agent/tool/DoctorSearchTool.java`
- Create: `com/medical/ai/agent/tool/AppointmentTool.java`
- Create: `com/medical/ai/agent/tool/KnowledgeSearchTool.java`

**Step 1: DoctorSearchTool**

```java
package com.medical.ai.agent.tool;

import com.medical.api.doctor.RemoteDoctorService;
import com.medical.api.doctor.dto.DoctorInfoDTO;
import com.medical.api.doctor.dto.SlotInfoDTO;
import com.medical.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Component
public class DoctorSearchTool {

    private final RemoteDoctorService remoteDoctorService;

    public DoctorSearchTool(RemoteDoctorService remoteDoctorService) {
        this.remoteDoctorService = remoteDoctorService;
    }

    /** 根据症状关键词搜索匹配的医生 */
    @Description("根据患者症状关键词搜索推荐的科室和医生。输入为逗号分隔的症状关键词。")
    public Function<SearchDoctorRequest, List<DoctorInfoDTO>> searchDoctorBySymptom() {
        return request -> {
            R<List<DoctorInfoDTO>> result = remoteDoctorService.searchBySymptom(request.getKeywords());
            return result.isSuccess() ? result.getData() : List.of();
        };
    }

    /** 查询医生可用号源 */
    @Description("查询指定医生在指定日期的可预约时间段。")
    public Function<GetSlotsRequest, List<SlotInfoDTO>> getAvailableSlots() {
        return request -> {
            R<List<SlotInfoDTO>> result = remoteDoctorService.getAvailableSlots(
                    request.getDoctorId(), request.getDate());
            return result.isSuccess() ? result.getData() : List.of();
        };
    }
}
// Request DTOs 省略，包含 keywords / doctorId+date 字段
```

**Step 2: AppointmentTool**

```java
// 调用 RemoteAppointmentService.createAppointment(patientId, doctorId, slotId)
// 返回预约结果
```

**Step 3: KnowledgeSearchTool**

```java
// 调用 RemoteKnowledgeService.search(query, topK)
// 返回知识库检索结果列表
```

---

## Task 6: Agent 定义 - System Prompt + Tool 绑定

**Files:**
- Create: `com/medical/ai/agent/TriageAgent.java` (导诊)
- Create: `com/medical/ai/agent/MedicalQaAgent.java` (医疗问答)
- Create: `com/medical/ai/agent/SummaryAgent.java` (对话摘要)
- Create: `com/medical/ai/agent/EncyclopediaAgent.java` (医生百科)
- Create: `com/medical/ai/agent/AgentFactory.java` (Agent 工厂)

**Step 1: TriageAgent（导诊 Agent）**

System Prompt 核心要点：
```text
你是一位专业的AI医疗分诊助手。你的职责是：
1. 通过多轮对话收集患者的症状信息（主诉、伴随症状、持续时间、严重程度）
2. 在收集到足够信息后，调用 searchDoctorBySymptom 工具为患者推荐合适的科室和医生
3. 当患者选择医生后，调用 getAvailableSlots 查询可用号源
4. 当患者确认时间后，调用 createAppointment 完成预约

规则：
- 不要在第一轮就推荐科室，至少询问2-3个问题
- 使用通俗易懂的语言，不要过度使用医学术语
- 如果症状紧急（如胸痛、呼吸困难），立即建议拨打120
- 声明：AI导诊仅供参考，不能替代专业医生诊断
```

绑定工具：`searchDoctorBySymptom`, `getAvailableSlots`, `createAppointment`

**Step 2: MedicalQaAgent（医疗问答 Agent）**

System Prompt 核心要点：
```text
你是一位医学科普助手。基于提供的知识库内容回答健康问题。
1. 优先引用知识库中的内容作答
2. 如果知识库没有相关内容，可以用你的通用知识回答，但要注明
3. 回答要科学、准确、通俗易懂
4. 在适当时机建议用户就医
```

绑定工具：`searchKnowledge`（RAG 检索）

**Step 3: SummaryAgent（对话摘要 Agent）**

System Prompt：
```text
你是一位医疗对话分析助手。请分析以下患者与AI导诊助手的对话记录，生成结构化的就诊前摘要。

输出格式（JSON）：
{
  "chiefComplaint": "主诉",
  "symptoms": "伴随症状",
  "duration": "持续时间",
  "severity": "严重程度",
  "medicalHistory": "既往史",
  "aiAssessment": "AI初步判断"
}
```

无工具绑定，纯 Prompt 驱动。

**Step 4: EncyclopediaAgent（医生百科 Agent）**

System Prompt 核心要点：
```text
你是一位专业的医学百科助手，面向医生提供专业知识查询服务。
可以查询药品信息、临床指南、病理知识等。
使用专业医学术语，提供详细的学术级回答。
```

绑定工具：`searchKnowledge`

**Step 5: AgentFactory**

```java
package com.medical.ai.agent;

@Component
@RequiredArgsConstructor
public class AgentFactory {
    private final TriageAgent triageAgent;
    private final MedicalQaAgent medicalQaAgent;
    private final SummaryAgent summaryAgent;
    private final EncyclopediaAgent encyclopediaAgent;

    public Agent getAgent(String agentType) {
        return switch (agentType) {
            case "TRIAGE" -> triageAgent;
            case "QA" -> medicalQaAgent;
            case "SUMMARY" -> summaryAgent;
            case "ENCYCLOPEDIA" -> encyclopediaAgent;
            default -> medicalQaAgent; // 默认问答
        };
    }
}
```

---

## Task 7: Service - 对话管理

**Files:**
- Create: `com/medical/ai/service/ChatService.java`
- Create: `com/medical/ai/service/impl/ChatServiceImpl.java`

功能清单：
- `createSession(userId, sessionType)` - 创建会话
- `listSessions(userId)` - 用户会话列表
- `getSessionMessages(sessionId)` - 会话消息历史
- `chat(sessionId, userId, message)` → `Flux<SseMessageVO>` - **核心方法 SSE 流式对话**
- `endSession(sessionId)` - 结束会话
- `deleteSession(sessionId)` - 删除会话

**核心 chat 方法流程：**

```java
public Flux<SseMessageVO> chat(Long sessionId, Long userId, String message) {
    // 1. 保存用户消息到 DB
    // 2. 加载会话历史消息（最近20条作为上下文）
    // 3. 根据 sessionType 获取对应 Agent
    // 4. 如果是问答类 Agent，先做 RAG 检索，将结果注入上下文
    // 5. 调用 Spring AI ChatClient.stream()，传入历史消息 + System Prompt + Tools
    // 6. 在 Flux 中逐 token 返回 SSE
    // 7. Flux 完成后，保存完整的 assistant 消息到 DB
    // 8. 异步调用 TTS 生成音频URL，通过额外 SSE 事件推送
    // 9. 如果 Agent 触发了 Function Calling，处理工具调用结果并继续对话
}
```

---

## Task 8: Service - TTS 语音合成

**Files:**
- Create: `com/medical/ai/service/TtsService.java`
- Create: `com/medical/ai/service/impl/AliyunTtsServiceImpl.java`

```java
public interface TtsService {
    /** 将文本转为语音，返回音频文件URL */
    String synthesize(String text);
}
```

实现要点：
- 调用阿里云智能语音 REST API
- 将音频数据保存到本地/OSS
- 返回可访问的 URL
- 音色选择：女声温柔音色（如 xiaoyun）
- 音频格式：MP3
- 超过 300 字的文本分段合成

application.yml：
```yaml
aliyun:
  tts:
    access-key-id: ${ALIYUN_AK_ID:your-key}
    access-key-secret: ${ALIYUN_AK_SECRET:your-secret}
    app-key: ${ALIYUN_TTS_APPKEY:your-appkey}
    voice: xiaoyun
    format: mp3
    sample-rate: 16000
    speech-rate: 0
    volume: 50
```

---

## Task 9: Service - 对话摘要（异步触发）

**Files:**
- Create: `com/medical/ai/service/SummaryService.java`
- Create: `com/medical/ai/service/impl/SummaryServiceImpl.java`

```java
public interface SummaryService {
    /** 为指定会话生成对话摘要（异步） */
    void generateSummary(Long sessionId, Long appointmentId);
    /** 查询摘要 */
    ConversationSummaryVO getSummaryBySession(Long sessionId);
    ConversationSummaryVO getSummaryByAppointment(Long appointmentId);
}
```

实现要点：
- 加载会话全部消息
- 拼装成 SummaryAgent 的输入
- 调用 ChatClient（非流式），解析 JSON 输出
- 存入 conversation_summary 表

---

## Task 10: Controller 层 - 对话接口

**Files:**
- Create: `com/medical/ai/controller/ChatController.java`

```java
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** 创建会话 */
    @PostMapping("/session")
    public R<ChatSessionVO> createSession(@RequestBody CreateSessionDTO dto) {
        Long userId = SecurityUtil.getUserId();
        return R.ok(chatService.createSession(userId, dto.getSessionType()));
    }

    /** 会话列表 */
    @GetMapping("/sessions")
    public R<List<ChatSessionVO>> listSessions() {
        return R.ok(chatService.listSessions(SecurityUtil.getUserId()));
    }

    /** 会话消息历史 */
    @GetMapping("/session/{sessionId}/messages")
    public R<List<ChatMessageVO>> getMessages(@PathVariable Long sessionId) {
        return R.ok(chatService.getSessionMessages(sessionId));
    }

    /** SSE 流式对话 -- 核心接口 */
    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<SseMessageVO>> chat(@RequestBody ChatRequestDTO dto) {
        Long userId = SecurityUtil.getUserId();
        return chatService.chat(dto.getSessionId(), userId, dto.getMessage())
                .map(msg -> ServerSentEvent.<SseMessageVO>builder()
                        .event(msg.getType())
                        .data(msg)
                        .build());
    }

    /** 结束会话 */
    @PostMapping("/session/{sessionId}/end")
    public R<Void> endSession(@PathVariable Long sessionId) {
        chatService.endSession(sessionId);
        return R.ok();
    }
}
```

---

## Task 11: Controller - 摘要接口（供医生端调用）

**Files:**
- Create: `com/medical/ai/controller/SummaryController.java`

```
GET /summary/session/{sessionId}        - 按会话查摘要
GET /summary/appointment/{appointmentId} - 按预约查摘要
```

---

## Task 12: Controller - 医生百科接口

**Files:**
- Create: `com/medical/ai/controller/EncyclopediaController.java`

```
POST /encyclopedia/chat (SSE)  - 百科对话（DOCTOR 角色）
GET  /encyclopedia/sessions    - 百科会话列表
```

---

## Task 13: 编译验证 + Commit

Run: `mvn clean compile -f medical-ai/pom.xml`

```bash
git add .
git commit -m "feat(ai-service): implement 4 AI agents, SSE chat, RAG, TTS, conversation summary"
```

---

## 检查清单

- [ ] DDL: chat_session, chat_message, conversation_summary
- [ ] Spring AI 双模型配置（DeepSeek + 通义千问）
- [ ] 4 个 Agent 各有独立 System Prompt
- [ ] Function Calling 工具：searchDoctorBySymptom, getAvailableSlots, createAppointment, searchKnowledge
- [ ] SSE 流式对话接口
- [ ] 对话历史上下文管理（最近20条）
- [ ] RAG 检索注入上下文
- [ ] 阿里云 TTS 语音合成
- [ ] 对话摘要异步生成（结构化 JSON）
- [ ] 医生百科独立入口
- [ ] Feign 调用 doctor/appointment/knowledge 服务
- [ ] 编译通过
