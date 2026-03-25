# Sentinel Protection Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a first batch of Sentinel-based flow control, degrade, and hotspot protection to the gateway and core backend services.

**Architecture:** Use a hybrid approach: the gateway blocks excessive traffic at entry points, while backend services own business-aware degrade and fallback behavior. Rules are initialized locally in code/config first, with a clean path to later migrate to Nacos.

**Tech Stack:** Spring Cloud Gateway, Spring Boot 3, Spring Cloud Alibaba Sentinel, Java 17, JUnit/MockMvc, Maven.

---

## Chunk 1: Dependency And Configuration Wiring

### Task 1: Add Sentinel dependencies

**Files:**
- Modify: `medical-ai/medical-gateway/pom.xml`
- Modify: `medical-ai/medical-service/medical-ai-service/pom.xml`
- Modify: `medical-ai/medical-service/medical-knowledge-service/pom.xml`
- Modify: `medical-ai/medical-service/medical-appointment-service/pom.xml`
- Modify: `medical-ai/medical-service/medical-doctor-service/pom.xml`

- [ ] Step 1: Inspect existing Spring Cloud Alibaba version management in parent POMs.
- [ ] Step 2: Add the minimal Sentinel starter dependencies needed for gateway and service integration.
- [ ] Step 3: Keep dependency changes DRY; avoid introducing dashboard/Nacos dynamic datasource dependencies in phase one.
- [ ] Step 4: Run `mvn -pl medical-gateway,medical-service/medical-ai-service,medical-service/medical-knowledge-service,medical-service/medical-appointment-service,medical-service/medical-doctor-service -am -f medical-ai/pom.xml -DskipTests compile`.

### Task 2: Add Sentinel base configuration

**Files:**
- Modify: `medical-ai/medical-gateway/src/main/resources/application.yml`
- Modify: `medical-ai/medical-service/medical-ai-service/src/main/resources/application.yml`
- Modify: `medical-ai/medical-service/medical-knowledge-service/src/main/resources/application.yml`
- Modify: `medical-ai/medical-service/medical-appointment-service/src/main/resources/application.yml`
- Modify: `medical-ai/medical-service/medical-doctor-service/src/main/resources/application.yml`

- [ ] Step 1: Add Sentinel enablement/config keys required by the chosen starters.
- [ ] Step 2: Keep configuration local-only; do not wire Nacos dynamic rule sources yet.
- [ ] Step 3: Re-run the compile command to verify config and dependency wiring.

## Chunk 2: Gateway Entry Limiting

### Task 3: Add gateway Sentinel resource mapping and block response

**Files:**
- Create: `medical-ai/medical-gateway/src/main/java/com/medical/gateway/config/SentinelGatewayConfig.java`
- Create: `medical-ai/medical-gateway/src/main/java/com/medical/gateway/handler/GatewayBlockRequestHandler.java`
- Test: `medical-ai/medical-gateway/src/test/java/...` (new focused config/handler test if gateway tests exist, otherwise add minimal unit test)

- [ ] Step 1: Write the failing test for gateway blocked response shape if a gateway test harness already exists.
- [ ] Step 2: Register gateway resources for `gw:auth:login`, `gw:auth:wxLogin`, `gw:ai:chatSend`, and `gw:knowledge:search`.
- [ ] Step 3: Implement unified blocked JSON response compatible with existing `R<T>` envelope style.
- [ ] Step 4: Add local `GatewayFlowRule` initialization with conservative default thresholds.
- [ ] Step 5: Run the focused gateway test/compile command.

## Chunk 3: AI Service Protection

### Task 4: Protect chat streaming resource

**Files:**
- Modify: `medical-ai/medical-service/medical-ai-service/src/main/java/com/medical/ai/controller/ChatController.java`
- Modify: `medical-ai/medical-service/medical-ai-service/src/main/java/com/medical/ai/service/impl/ChatServiceImpl.java`
- Create: `medical-ai/medical-service/medical-ai-service/src/main/java/com/medical/ai/config/SentinelRuleConfig.java`
- Create/Modify: focused tests under `medical-ai/medical-service/medical-ai-service/src/test/java/...`

- [ ] Step 1: Write a failing test for the blocked/degraded chat path if test harness coverage is available.
- [ ] Step 2: Mark the SSE entry or service method with Sentinel resource `svc:ai:chatStream`.
- [ ] Step 3: Implement block/fallback behavior that returns a stable busy response without triggering the long tool/TTS path.
- [ ] Step 4: Register local flow and degrade rules for `svc:ai:chatStream`.
- [ ] Step 5: Run the smallest AI-service test set covering controller/service behavior.

### Task 5: Protect TTS synthesis resource

**Files:**
- Modify: `medical-ai/medical-service/medical-ai-service/src/main/java/com/medical/ai/service/impl/TtsServiceImpl.java`
- Modify: `medical-ai/medical-service/medical-ai-service/src/main/java/com/medical/ai/service/impl/ChatServiceImpl.java`
- Modify: `medical-ai/medical-service/medical-ai-service/src/main/java/com/medical/ai/config/SentinelRuleConfig.java`
- Test: focused TTS-related test class

- [ ] Step 1: Write or update a failing test that demonstrates TTS degrade should preserve text-only behavior.
- [ ] Step 2: Wrap synthesis as resource `svc:ai:tts`.
- [ ] Step 3: Implement fallback that emits no audio URL / emits the existing TTS error path safely.
- [ ] Step 4: Add slow-call and exception-ratio rules for TTS.
- [ ] Step 5: Run the focused TTS/chat tests.

## Chunk 4: Knowledge Service Protection

### Task 6: Protect knowledge search resources

**Files:**
- Modify: `medical-ai/medical-service/medical-knowledge-service/src/main/java/com/medical/knowledge/controller/KnowledgeBaseController.java`
- Modify: `medical-ai/medical-service/medical-knowledge-service/src/main/java/com/medical/knowledge/service/impl/KnowledgeBaseServiceImpl.java`
- Create: `medical-ai/medical-service/medical-knowledge-service/src/main/java/com/medical/knowledge/config/SentinelRuleConfig.java`
- Test: `medical-ai/medical-service/medical-knowledge-service/src/test/java/...`

- [ ] Step 1: Write a failing test for external search degrade behavior.
- [ ] Step 2: Add resources `svc:knowledge:search` and `svc:knowledge:innerSearch`.
- [ ] Step 3: Implement separate degrade semantics for external API vs internal tool/search path.
- [ ] Step 4: Register local flow/degrade rules.
- [ ] Step 5: Run focused knowledge-service controller/service tests.

### Task 7: Protect embedding call resource

**Files:**
- Modify: `medical-ai/medical-service/medical-knowledge-service/src/main/java/com/medical/knowledge/service/impl/EmbeddingServiceImpl.java`
- Modify: `medical-ai/medical-service/medical-knowledge-service/src/main/java/com/medical/knowledge/config/SentinelRuleConfig.java`
- Test: embedding-focused unit or service test

- [ ] Step 1: Write a failing test that expects fast-fail or fallback on embedding degradation.
- [ ] Step 2: Wrap the external embedding call as `svc:knowledge:embed`.
- [ ] Step 3: Implement fallback behavior consistent with the caller context.
- [ ] Step 4: Add slow-call and exception-ratio rules.
- [ ] Step 5: Run focused embedding/knowledge tests.

## Chunk 5: Appointment And Doctor Hotspot Protection

### Task 8: Protect appointment create resource

**Files:**
- Modify: `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/controller/AppointmentController.java`
- Modify: `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/service/impl/AppointmentServiceImpl.java`
- Create: `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/config/SentinelRuleConfig.java`
- Test: appointment controller/service tests

- [ ] Step 1: Write a failing test for blocked appointment creation result.
- [ ] Step 2: Add resource `svc:appointment:create`.
- [ ] Step 3: Add param flow rule keyed by `slotId` plus overall flow rule.
- [ ] Step 4: Implement busy fallback response.
- [ ] Step 5: Run focused appointment tests.

### Task 9: Protect doctor slot query resource

**Files:**
- Modify: `medical-ai/medical-service/medical-doctor-service/src/main/java/com/medical/doctor/controller/ScheduleController.java`
- Modify: `medical-ai/medical-service/medical-doctor-service/src/main/java/com/medical/doctor/service/impl/...` (exact schedule service file)
- Create: `medical-ai/medical-service/medical-doctor-service/src/main/java/com/medical/doctor/config/SentinelRuleConfig.java`
- Test: doctor schedule tests

- [ ] Step 1: Write a failing test for blocked slot-query fallback.
- [ ] Step 2: Add resource `svc:doctor:scheduleSlots`.
- [ ] Step 3: Add param flow rule using doctor/date request dimensions that the method already receives.
- [ ] Step 4: Implement empty-list or busy-response fallback matching current API contract.
- [ ] Step 5: Run focused doctor schedule tests.

## Chunk 6: End-To-End Verification And Documentation

### Task 10: Verify compilation and targeted tests

**Files:**
- Modify: none expected unless verification exposes gaps

- [ ] Step 1: Run targeted module tests for all touched services.
- [ ] Step 2: Run a compile/build command covering gateway + touched services.
- [ ] Step 3: If feasible, run one minimal integration path for login/chat/search/appointment.
- [ ] Step 4: Record the exact commands and outcomes in `progress.md` after implementation.

### Task 11: Add migration notes for future Nacos rule source

**Files:**
- Modify: `findings.md`
- Optionally modify: a deployment/config doc under `docs/`

- [ ] Step 1: Document that rules are currently local-initialized.
- [ ] Step 2: Note the planned migration path to Nacos dynamic data source.
- [ ] Step 3: Keep the note short and operational.

Plan complete and saved to `docs/superpowers/plans/2026-03-23-sentinel-protection-implementation.md`. Ready to execute?
