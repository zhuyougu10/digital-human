package com.medical.ai.service.impl;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.ai.agent.Agent;
import com.medical.ai.agent.AgentFactory;
import com.medical.ai.domain.entity.ChatMessage;
import com.medical.ai.domain.entity.ChatSession;
import com.medical.ai.domain.vo.ChatMessageVO;
import com.medical.ai.domain.vo.ChatSessionVO;
import com.medical.ai.domain.vo.SseMessageVO;
import com.medical.ai.mapper.ChatMessageMapper;
import com.medical.ai.mapper.ChatSessionMapper;
import com.medical.ai.service.ChatService;
import com.medical.ai.service.SummaryService;
import com.medical.ai.service.TtsService;
import com.medical.api.appointment.RemoteAppointmentService;
import com.medical.api.appointment.dto.AppointmentDTO;
import com.medical.api.doctor.RemoteScheduleService;
import com.medical.api.doctor.dto.SlotInfoDTO;
import com.medical.common.core.domain.R;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    public static final String CHAT_STREAM_RESOURCE = "svc:ai:chatStream";
    private static final int MAX_CONTEXT_MESSAGES = 20;
    private static final Pattern APPOINTMENT_ID_VALUE_PATTERN = Pattern.compile("(?:appointmentId|\u9884\u7ea6ID|\u9884\u7ea6\u7f16\u53f7)\\s*[:\uFF1A=]?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");
    private static final String DEFAULT_SESSION_TITLE = "新对话";
    private static final Pattern APPOINTMENT_SUCCESS_PATTERN = Pattern.compile("预约成功|成功创建了预约|已经为您成功创建了预约");
    private static final Pattern APPOINTMENT_ID_PATTERN = Pattern.compile("预约ID|appointmentId", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOCTOR_ID_PATTERN = Pattern.compile("(?:doctorId|医生ID)\s*[:：]\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SLOT_ID_PATTERN = Pattern.compile("(?:slotId|时间段ID)\s*(?:为|是|=|:|：)?\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2})[年/-](\\d{1,2})[月/-](\\d{1,2})");
    private static final Pattern CONFIRM_PERIOD_PATTERN = Pattern.compile("确认[^。\n]{0,20}(上午|下午)");
    private static final Pattern APPOINTMENT_PERIOD_PATTERN = Pattern.compile("预约[^。\n]{0,20}(上午|下午)");
    private static final Pattern SIMPLE_PERIOD_PATTERN = Pattern.compile("(上午|下午)");
    private static final String APPOINTMENT_GUARD_FALLBACK_REPLY = "抱歉，刚才尚未成功创建预约，请重新确认医生与时间后，我再为您提交预约。";
    private static final String TRIAGE_TTS_EXCLUDED_DISCLAIMER = "AI导诊仅供参考，不能替代专业医生诊断";
    private static final String AVATAR_CUE_KEY = "avatarCue";
    private static final String CUE_BUCKET_KEY = "bucket";
    private static final String CUE_EXPRESSION_KEY = "expression";
    private static final String CUE_ACTION_KEY = "action";
    private static final String CUE_TONE_KEY = "tone";
    private static final String CUE_VARIANT_KEY = "variant";
    private static final String CUE_SOURCE_KEY = "source";
    private static final String SUGGESTED_REPLIES_KEY = "suggestedReplies";
    private static final int MAX_SUGGESTED_REPLY_LENGTH = 20;
    private static final int MAX_SUGGESTED_REPLY_COUNT = 3;
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final AgentFactory agentFactory;
    private final OpenAiChatModel chatModel;
    private final TtsService ttsService;
    private final SummaryService summaryService;
    private final RemoteAppointmentService remoteAppointmentService;
    private final RemoteScheduleService remoteScheduleService;
    private final TriageAppointmentFlowService triageAppointmentFlowService;
    private final ObjectMapper objectMapper;

    @Override
    public ChatSessionVO createSession(Long userId, String sessionType) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setSessionType(sessionType);
        session.setTitle(DEFAULT_SESSION_TITLE);

        String agentType = switch (sessionType) {
            case "TRIAGE" -> "TRIAGE";
            case "ENCYCLOPEDIA" -> "ENCYCLOPEDIA";
            default -> "QA";
        };
        session.setAgentType(agentType);
        session.setStatus(0);
        sessionMapper.insert(session);
        return toSessionVO(session);
    }

    @Override
    public List<ChatSessionVO> listSessions(Long userId) {
        List<ChatSession> sessions = sessionMapper.selectList(
            new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .eq(ChatSession::getDeleted, 0)
                .orderByDesc(ChatSession::getUpdateTime)
        );
        return sessions.stream().map(this::toSessionVO).collect(Collectors.toList());
    }

    @Override
    public List<ChatMessageVO> getSessionMessages(Long sessionId, Long userId) {
        assertSessionOwner(sessionId, userId);
        List<ChatMessage> messages = messageMapper.selectList(
            new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreateTime)
        );
        return messages.stream().map(this::toMessageVO).collect(Collectors.toList());
    }

    @Override
    public Flux<SseMessageVO> chat(Long sessionId, Long userId, String message) {
        ChatSession session = assertSessionOwner(sessionId, userId);
        final Entry sentinelEntry;
        try {
            sentinelEntry = SphU.entry(CHAT_STREAM_RESOURCE);
        } catch (BlockException e) {
            return Flux.error(new BusinessException(ErrorCode.AI_RATE_LIMIT));
        }

        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(message);
        messageMapper.insert(userMsg);

        if ("TRIAGE".equals(session.getAgentType())) {
            TriageAppointmentFlowService.TriageFlowResult triageResult = triageAppointmentFlowService.handle(
                    sessionId,
                    userId,
                    loadSessionMessages(sessionId));
            if (triageResult != null) {
                return buildDeterministicTriageResponse(session, sessionId, message, triageResult)
                        .doFinally(signalType -> sentinelEntry.exit());
            }
        }

        Agent agent = agentFactory.getAgent(session.getAgentType());
        List<Message> chatMessages = buildChatMessages(sessionId, agent, userId);

        List<String> toolNames = agent.getToolNames();
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder();
        if (toolNames != null && !toolNames.isEmpty()) {
            optionsBuilder.withFunctions(new HashSet<>(toolNames));
        }
        Prompt prompt = new Prompt(chatMessages, optionsBuilder.build());

        StringBuilder fullResponse = new StringBuilder();
        StringBuilder ttsSentenceBuffer = new StringBuilder();
        AtomicReference<String> fullTextRef = new AtomicReference<>("");
        AtomicReference<ChatMessage> assistantMessageRef = new AtomicReference<>();
        AtomicReference<String> firstTtsUrlRef = new AtomicReference<>();
        AtomicInteger nextTtsSegmentIndex = new AtomicInteger(0);
        AtomicInteger pendingTtsTasks = new AtomicInteger(0);
        AtomicBoolean deferRemainingSentenceTts = new AtomicBoolean(false);
        AtomicBoolean ttsSchedulingComplete = new AtomicBoolean(false);
        List<String> earlySpokenSentences = Collections.synchronizedList(new ArrayList<>());
        Sinks.Many<SseMessageVO> ttsSink = Sinks.many().unicast().onBackpressureBuffer();

        Flux<SseMessageVO> chatEvents = chatModel.stream(prompt)
            .publishOn(Schedulers.boundedElastic())
            .map(chatResponse -> {
                String token = "";
                if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                    token = chatResponse.getResult().getOutput().getContent();
                    if (token == null) {
                        token = "";
                    }
                }
                fullResponse.append(token);
                ttsSentenceBuffer.append(token);
                List<String> readySentences = filterSpeakableTtsSentences(drainTtsSentences(ttsSentenceBuffer, false));
                for (String sentence : readySentences) {
                    if (!deferRemainingSentenceTts.get() && !shouldDeferSentenceLevelTts(session.getAgentType(), sentence)) {
                        earlySpokenSentences.add(sentence);
                        scheduleTtsSynthesis(
                            ttsSink,
                            pendingTtsTasks,
                            ttsSchedulingComplete,
                            assistantMessageRef,
                            firstTtsUrlRef,
                            sessionId,
                            sentence,
                            nextTtsSegmentIndex.getAndIncrement(),
                            null,
                            buildAvatarCueMetadata(session, message, sentence)
                        );
                    } else {
                        deferRemainingSentenceTts.set(true);
                    }
                }
                SseMessageVO vo = new SseMessageVO();
                vo.setType("token");
                vo.setContent(token);
                return vo;
            })
            .doOnError(e -> {
                Tracer.trace(e);
                log.error("Chat stream error for session {}: {}", sessionId, e.getMessage(), e);
                ttsSink.tryEmitError(e);
            })
            .concatWith(Mono.fromCallable(() -> {
                String fullText = fullResponse.toString();
                String guardedText = guardAppointmentSuccessReply(sessionId, userMsg.getId(), userId, message, fullText);
                String normalizedAssistantText = enforceSingleTriageQuestionTurn(session, guardedText);
                fullTextRef.set(normalizedAssistantText);
                Map<String, Object> metadata = buildAssistantMetadata(session, message, normalizedAssistantText);

                // 先保存 assistant 消息，保证完整文本先落库
                ChatMessage assistantMsg = new ChatMessage();
                assistantMsg.setSessionId(sessionId);
                assistantMsg.setRole("assistant");
                assistantMsg.setContent(normalizedAssistantText);
                assistantMsg.setMetadata(writeMetadataJson(metadata));
                messageMapper.insert(assistantMsg);
                assistantMessageRef.set(assistantMsg);

                // 保留原有标题更新逻辑
                if (DEFAULT_SESSION_TITLE.equals(session.getTitle()) && message != null && !message.isEmpty()) {
                    session.setTitle(message.length() > 20 ? message.substring(0, 20) + "..." : message);
                    sessionMapper.updateById(session);
                }

                List<String> finalSentences = filterSpeakableTtsSentences(splitTtsSentences(normalizedAssistantText));
                int totalSegments = finalSentences.size();
                if (firstTtsUrlRef.get() != null) {
                    assistantMsg.setTtsUrl(firstTtsUrlRef.get());
                    messageMapper.updateById(assistantMsg);
                }
                for (int i = earlySpokenSentences.size(); i < finalSentences.size(); i++) {
                    scheduleTtsSynthesis(
                        ttsSink,
                        pendingTtsTasks,
                        ttsSchedulingComplete,
                        assistantMessageRef,
                        firstTtsUrlRef,
                        sessionId,
                        finalSentences.get(i),
                        i,
                        totalSegments,
                        buildAvatarCueMetadata(session, message, guardedText)
                    );
                }

                // 先下发 complete，句级 TTS 在独立异步链路中继续推进
                SseMessageVO complete = new SseMessageVO();
                complete.setType("complete");
                complete.setContent(normalizedAssistantText);
                complete.setTtsUrl(null);
                complete.setMetadata(metadata);
                complete.setTotalSegments(totalSegments);
                ttsSchedulingComplete.set(true);
                if (pendingTtsTasks.get() == 0) {
                    ttsSink.tryEmitComplete();
                }
                return complete;
            }))
            ;

        return Flux.merge(chatEvents, ttsSink.asFlux())
            .doFinally(signalType -> sentinelEntry.exit());
    }

    private List<ChatMessage> loadSessionMessages(Long sessionId) {
        return messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreateTime)
        );
    }

    private Flux<SseMessageVO> buildDeterministicTriageResponse(ChatSession session,
                                                                Long sessionId,
                                                                String userMessage,
                                                                TriageAppointmentFlowService.TriageFlowResult result) {
        return Mono.fromCallable(() -> {
            String reply = result.reply();
            Map<String, Object> metadata = buildAssistantMetadata(session, userMessage, reply);

            ChatMessage assistantMsg = new ChatMessage();
            assistantMsg.setSessionId(sessionId);
            assistantMsg.setRole("assistant");
            assistantMsg.setContent(reply);
            assistantMsg.setMetadata(writeMetadataJson(metadata));
            messageMapper.insert(assistantMsg);

            if (DEFAULT_SESSION_TITLE.equals(session.getTitle()) && userMessage != null && !userMessage.isEmpty()) {
                session.setTitle(userMessage.length() > 20 ? userMessage.substring(0, 20) + "..." : userMessage);
                sessionMapper.updateById(session);
            }

            SseMessageVO token = new SseMessageVO();
            token.setType("token");
            token.setContent(reply);

            SseMessageVO complete = new SseMessageVO();
            complete.setType("complete");
            complete.setContent(reply);
            complete.setMetadata(metadata);

            List<SseMessageVO> events = new ArrayList<>();
            events.add(token);
            events.add(complete);

            try {
                String ttsUrl = ttsService.synthesize(reply);
                if (ttsUrl != null && !ttsUrl.isBlank()) {
                    assistantMsg.setTtsUrl(ttsUrl);
                    messageMapper.updateById(assistantMsg);

                    SseMessageVO tts = new SseMessageVO();
                    tts.setType("tts");
                    tts.setTtsUrl(ttsUrl);
                    tts.setMetadata(metadata);
                    tts.setSegmentIndex(0);
                    tts.setTotalSegments(1);
                    events.add(tts);
                }
            } catch (Exception e) {
                log.warn("Deterministic triage TTS failed, sessionId={}, error={}", sessionId, e.getMessage());
            }
            return events;
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable);
    }

    @Override
    public void endSession(Long sessionId, Long userId) {
        ChatSession session = assertSessionOwner(sessionId, userId);

        session.setStatus(1);
        sessionMapper.updateById(session);
        if ("TRIAGE".equals(session.getSessionType())) {
            try {
                summaryService.generateSummary(sessionId, resolveAppointmentIdForSession(sessionId));
            } catch (Exception e) {
                log.warn("摘要生成失败: {}", e.getMessage());
            }
        }
    }

    @Override
    public void deleteSession(Long sessionId, Long userId) {
        assertSessionOwner(sessionId, userId);
        sessionMapper.deleteById(sessionId);
    }

    private Long resolveAppointmentIdForSession(Long sessionId) {
        try {
            R<AppointmentDTO> response = remoteAppointmentService.getAppointmentBySession(sessionId);
            if (response != null && response.isSuccess() && response.getData() != null) {
                return response.getData().getId();
            }
        } catch (Exception e) {
            log.warn("Failed to resolve appointment for session {}: {}", sessionId, e.getMessage());
        }
        return null;
    }

    private ChatSession assertSessionOwner(Long sessionId, Long userId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!Objects.equals(session.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return session;
    }

    private List<Message> buildChatMessages(Long sessionId, Agent agent) {
        return buildChatMessages(sessionId, agent, null);
    }

    private List<Message> buildChatMessages(Long sessionId, Agent agent, Long userId) {
        List<Message> messages = new ArrayList<>();
        String systemPrompt = agent.getSystemPrompt();
        if ("TRIAGE".equals(agent.getAgentType()) && userId != null) {
            systemPrompt += "\n\n当前患者信息：\n- patientId = " + userId
                + "\n- sessionId = " + sessionId
                + "\n在调用 createAppointment 工具时，请务必使用上面的 patientId。"
                + "\n在调用 createAppointment 工具时，请务必使用上面的 sessionId。"
                + "\n只有在 createAppointment 工具明确返回 success=true 且 appointmentId 非空时，才允许回复“预约成功”或输出预约ID；"
                + "若未实际调用 createAppointment，或工具返回失败/缺少appointmentId，必须明确告知“尚未创建预约”，并引导用户重试。";
        }
        messages.add(new SystemMessage(systemPrompt));

        List<ChatMessage> history = messageMapper.selectList(
            new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getCreateTime)
                .last("LIMIT " + MAX_CONTEXT_MESSAGES)
        );
        Collections.reverse(history);

        for (ChatMessage msg : history) {
            if ("user".equals(msg.getRole())) {
                messages.add(new UserMessage(msg.getContent()));
            } else if ("assistant".equals(msg.getRole())) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }
        return messages;
    }

    private ChatSessionVO toSessionVO(ChatSession session) {
        ChatSessionVO vo = new ChatSessionVO();
        vo.setId(session.getId());
        vo.setUserId(session.getUserId());
        vo.setSessionType(session.getSessionType());
        vo.setTitle(session.getTitle());
        vo.setAgentType(session.getAgentType());
        vo.setStatus(session.getStatus());
        vo.setCreateTime(session.getCreateTime());
        vo.setUpdateTime(session.getUpdateTime());
        return vo;
    }

    private ChatMessageVO toMessageVO(ChatMessage msg) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(msg.getId());
        vo.setSessionId(msg.getSessionId());
        vo.setRole(msg.getRole());
        vo.setContent(msg.getContent());
        vo.setToolCallId(msg.getToolCallId());
        vo.setToolName(msg.getToolName());
        vo.setTtsUrl(msg.getTtsUrl());
        vo.setCreateTime(msg.getCreateTime());

        if (msg.getMetadata() != null && !msg.getMetadata().isEmpty()) {
            try {
                vo.setMetadata(objectMapper.readValue(msg.getMetadata(), new TypeReference<Map<String, Object>>() {
                }));
            } catch (Exception e) {
                log.warn("Failed to parse metadata JSON: {}", e.getMessage());
            }
        }
        return vo;
    }

    private Map<String, Object> buildAvatarCueMetadata(ChatSession session, String userText, String assistantText) {
        String agentType = session != null ? session.getAgentType() : null;
        String bucket = resolveAvatarCueBucket(agentType, userText, assistantText);
        Map<String, Object> avatarCue = new LinkedHashMap<>();
        avatarCue.put(CUE_BUCKET_KEY, bucket);
        avatarCue.put(CUE_EXPRESSION_KEY, resolveAvatarCueExpression(bucket));
        avatarCue.put(CUE_ACTION_KEY, resolveAvatarCueAction(bucket));
        avatarCue.put(CUE_TONE_KEY, resolveAvatarCueTone(bucket));
        avatarCue.put(CUE_VARIANT_KEY, resolveAvatarCueVariant(bucket));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(AVATAR_CUE_KEY, avatarCue);
        metadata.put(CUE_SOURCE_KEY, resolveAvatarCueSource(agentType));
        return metadata;
    }

    private Map<String, Object> buildAssistantMetadata(ChatSession session, String userText, String assistantText) {
        Map<String, Object> metadata = buildAvatarCueMetadata(session, userText, assistantText);
        List<String> suggestedReplies = generateSuggestedReplies(session, userText, assistantText);
        if (!suggestedReplies.isEmpty()) {
            metadata.put(SUGGESTED_REPLIES_KEY, suggestedReplies);
        }
        return metadata;
    }

    private List<String> generateSuggestedReplies(ChatSession session, String userText, String assistantText) {
        if (!shouldGenerateSuggestedReplies(session, assistantText)) {
            return Collections.emptyList();
        }

        try {
            Prompt prompt = new Prompt(List.of(
                new SystemMessage("你是医疗导诊对话的建议回复生成器。请基于 assistant 的最后一句问句，为患者生成 3 条简短中文回复建议。"
                    + "仅输出 JSON 数组字符串，例如 [\"三天了\",\"伴有发烧\",\"没有其他症状\"]。"
                    + "每条都必须是患者可直接发送的一句话，不要解释，不要编号。"),
                new UserMessage("用户上一条消息：\n" + safeText(userText)
                    + "\n\nassistant 最终回复：\n" + safeText(assistantText))
            ));
            String response = chatModel.call(prompt).getResult().getOutput().getContent();
            List<String> normalizedReplies = normalizeSuggestedReplies(parseSuggestedReplies(response));
            if (!normalizedReplies.isEmpty()) {
                return normalizedReplies;
            }
        } catch (Exception e) {
            log.warn("Failed to generate suggested replies for agentType={}: {}",
                session != null ? session.getAgentType() : null,
                e.getMessage());
        }

        return buildFallbackSuggestedReplies(assistantText);
    }

    private boolean shouldGenerateSuggestedReplies(ChatSession session, String assistantText) {
        if (session == null || assistantText == null || assistantText.isBlank()) {
            return false;
        }
        return "TRIAGE".equals(session.getAgentType()) && isLastSentenceQuestion(assistantText);
    }

    private boolean isLastSentenceQuestion(String assistantText) {
        if (assistantText == null || assistantText.isBlank()) {
            return false;
        }
        String trimmed = assistantText.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        char lastChar = trimmed.charAt(trimmed.length() - 1);
        return lastChar == '?' || lastChar == '？';
    }

    private List<String> parseSuggestedReplies(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(rawResponse, new TypeReference<List<String>>() {
            });
        } catch (Exception ignored) {
            // fall through
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(rawResponse, new TypeReference<Map<String, Object>>() {
            });
            Object suggestedReplies = payload.get(SUGGESTED_REPLIES_KEY);
            if (suggestedReplies instanceof List<?> list) {
                return list.stream().map(item -> item == null ? null : String.valueOf(item)).toList();
            }
        } catch (Exception ignored) {
            // fall through
        }

        return rawResponse.lines()
            .map(String::trim)
            .map(line -> line.replaceFirst("^[\\-•*\\d.、\\)\\(\\s]+", ""))
            .toList();
    }

    private List<String> normalizeSuggestedReplies(List<String> replies) {
        if (replies == null || replies.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> normalizedReplies = new LinkedHashSet<>();
        for (String reply : replies) {
            String normalized = normalizeSuggestedReply(reply);
            if (normalized == null) {
                continue;
            }
            normalizedReplies.add(normalized);
            if (normalizedReplies.size() >= MAX_SUGGESTED_REPLY_COUNT) {
                break;
            }
        }
        return new ArrayList<>(normalizedReplies);
    }

    private String normalizeSuggestedReply(String reply) {
        if (reply == null) {
            return null;
        }
        String normalized = WHITESPACE_PATTERN.matcher(reply.trim()).replaceAll(" ");
        if (normalized.isBlank() || normalized.length() > MAX_SUGGESTED_REPLY_LENGTH) {
            return null;
        }
        return normalized;
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }

    private String enforceSingleTriageQuestionTurn(ChatSession session, String assistantText) {
        if (session == null || assistantText == null || assistantText.isBlank()) {
            return assistantText;
        }
        if (!"TRIAGE".equals(session.getAgentType())) {
            return assistantText;
        }

        int firstQuestionIndex = findFirstQuestionMarkIndex(assistantText);
        if (firstQuestionIndex < 0) {
            return assistantText;
        }

        int secondQuestionIndex = findNextQuestionMarkIndex(assistantText, firstQuestionIndex + 1);
        if (secondQuestionIndex < 0) {
            return assistantText;
        }

        String truncated = assistantText.substring(0, firstQuestionIndex + 1).trim();
        if (truncated.isEmpty()) {
            return assistantText;
        }

        if (assistantText.contains(TRIAGE_TTS_EXCLUDED_DISCLAIMER) && !truncated.contains(TRIAGE_TTS_EXCLUDED_DISCLAIMER)) {
            return truncated + TRIAGE_TTS_EXCLUDED_DISCLAIMER + "。";
        }
        return truncated;
    }

    private int findFirstQuestionMarkIndex(String text) {
        return findNextQuestionMarkIndex(text, 0);
    }

    private int findNextQuestionMarkIndex(String text, int startIndex) {
        if (text == null || text.isEmpty() || startIndex >= text.length()) {
            return -1;
        }
        for (int i = Math.max(0, startIndex); i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '?' || current == '？') {
                return i;
            }
        }
        return -1;
    }

    private List<String> buildFallbackSuggestedReplies(String assistantText) {
        if (assistantText == null || assistantText.isBlank()) {
            return Collections.emptyList();
        }

        List<String> fallbackReplies = new ArrayList<>();
        String normalizedText = assistantText.replace('\r', '\n');

        if (containsAny(normalizedText, "持续多久", "多久了", "几天了", "多长时间")) {
            fallbackReplies.add("今天刚开始");
            fallbackReplies.add("已经好几天了");
        }
        if (containsAny(normalizedText, "部位", "偏头痛", "后脑勺", "太阳穴")) {
            fallbackReplies.add("太阳穴附近痛");
            fallbackReplies.add("后脑勺疼");
        }
        if (containsAny(normalizedText, "其他不舒服", "发烧", "恶心", "呕吐", "头晕", "视力模糊", "脖子僵硬")) {
            fallbackReplies.add("伴有头晕");
            fallbackReplies.add("没有其他症状");
        }

        if (fallbackReplies.isEmpty()) {
            fallbackReplies.add("今天刚开始");
            fallbackReplies.add("已经好几天了");
            fallbackReplies.add("没有其他症状");
        }

        if (fallbackReplies.size() < MAX_SUGGESTED_REPLY_COUNT) {
            fallbackReplies.add("今天刚开始");
            fallbackReplies.add("已经好几天了");
            fallbackReplies.add("没有其他症状");
        }

        return normalizeSuggestedReplies(fallbackReplies);
    }

    private String resolveAvatarCueBucket(String agentType, String userText, String assistantText) {
        if (!"TRIAGE".equals(agentType)) {
            return "knowledge_explanation";
        }

        String text = ((assistantText == null ? "" : assistantText) + "\n" + (userText == null ? "" : userText));
        String normalized = text.toLowerCase();

        if (containsAny(normalized, "预约失败", "挂号失败", "未能预约", "预约未成功", "未成功创建预约")) {
            return "appointment_failure";
        }
        if (containsAny(normalized, "抱歉", "尚未成功", "暂时无法", "无法完成", "请重新确认", "tts 超时")) {
            return "fallback_error";
        }
        if (containsAny(normalized, "急诊", "胸痛", "呼吸困难", "昏迷", "出血", "立即就医", "尽快就医", "紧急")) {
            return "urgent_warning";
        }
        if (containsAny(normalized, "预约成功", "成功创建了预约", "已经为您成功创建了预约", "appointmentid")) {
            return "appointment_success";
        }
        if (containsAny(normalized, "预约", "号源", "时间段", "上午", "下午", "选择", "确认")) {
            return "slot_selection";
        }
        if (containsAny(normalized, "推荐", "科室", "医生", "挂号")) {
            return "doctor_recommendation";
        }
        if (containsAny(normalized, "症状", "哪里不舒服", "哪里疼", "不适", "多长时间", "发热", "咳嗽", "头痛")) {
            return "symptom_collection";
        }
        if (containsAny(normalized, "你好", "您好", "hello", "hi", "早上好", "晚上好")) {
            return "greeting";
        }
        return "symptom_collection";
    }

    private String resolveAvatarCueExpression(String bucket) {
        return switch (bucket) {
            case "greeting" -> "warm";
            case "symptom_collection" -> "attentive";
            case "doctor_recommendation" -> "confident";
            case "slot_selection" -> "focused";
            case "appointment_success" -> "relieved";
            case "appointment_failure", "fallback_error" -> "apologetic";
            case "knowledge_explanation" -> "calm";
            case "urgent_warning" -> "serious";
            default -> "attentive";
        };
    }

    private String resolveAvatarCueAction(String bucket) {
        return switch (bucket) {
            case "greeting" -> "welcome";
            case "symptom_collection" -> "listen";
            case "doctor_recommendation" -> "recommend";
            case "slot_selection" -> "guide";
            case "appointment_success" -> "celebrate";
            case "appointment_failure", "fallback_error" -> "apologize";
            case "knowledge_explanation" -> "explain";
            case "urgent_warning" -> "alert";
            default -> "listen";
        };
    }

    private String resolveAvatarCueTone(String bucket) {
        return switch (bucket) {
            case "appointment_success" -> "positive";
            case "urgent_warning", "appointment_failure", "fallback_error" -> "serious";
            case "knowledge_explanation" -> "neutral";
            default -> "supportive";
        };
    }

    private String resolveAvatarCueVariant(String bucket) {
        return switch (bucket) {
            case "appointment_success", "appointment_failure", "fallback_error" -> "transactional";
            case "doctor_recommendation", "slot_selection" -> "triage_flow";
            case "urgent_warning" -> "safety";
            default -> "general";
        };
    }

    private String resolveAvatarCueSource(String agentType) {
        if (agentType == null || agentType.isBlank()) {
            return "unknown";
        }
        return agentType;
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private List<String> splitTtsSentences(String text) {
        return drainTtsSentences(new StringBuilder(text == null ? "" : text), true);
    }

    private List<String> filterSpeakableTtsSentences(List<String> sentences) {
        if (sentences == null || sentences.isEmpty()) {
            return Collections.emptyList();
        }
        return sentences.stream()
            .filter(sentence -> !shouldSkipTtsSentence(sentence))
            .toList();
    }

    private List<String> drainTtsSentences(StringBuilder buffer, boolean flushRemainder) {
        List<String> sentences = new ArrayList<>();
        if (buffer == null || buffer.isEmpty()) {
            return sentences;
        }

        int sentenceStart = 0;
        for (int i = 0; i < buffer.length(); i++) {
            if (!isTtsSentenceBoundary(buffer.charAt(i))) {
                continue;
            }
            String sentence = buffer.substring(sentenceStart, i + 1).trim();
            if (!sentence.isBlank()) {
                sentences.add(sentence);
            }
            sentenceStart = i + 1;
        }

        if (flushRemainder) {
            String tail = buffer.substring(sentenceStart).trim();
            if (!tail.isBlank()) {
                sentences.add(tail);
            }
            buffer.setLength(0);
        } else if (sentenceStart > 0) {
            buffer.delete(0, sentenceStart);
        }

        return sentences;
    }

    private boolean isTtsSentenceBoundary(char currentChar) {
        return currentChar == '。'
            || currentChar == '！'
            || currentChar == '？'
            || currentChar == '!'
            || currentChar == '?'
            || currentChar == ';'
            || currentChar == '；';
    }

    private boolean shouldSkipTtsSentence(String sentence) {
        if (sentence == null || sentence.isBlank()) {
            return true;
        }

        String normalized = sentence.trim()
            .replaceAll("[。.!！?？；;]+$", "")
            .trim();
        return TRIAGE_TTS_EXCLUDED_DISCLAIMER.equals(normalized);
    }

    private boolean shouldDeferSentenceLevelTts(String agentType, String sentence) {
        if (!"TRIAGE".equals(agentType) || sentence == null || sentence.isBlank()) {
            return false;
        }

        String normalized = sentence.toLowerCase();
        return APPOINTMENT_SUCCESS_PATTERN.matcher(sentence).find()
            || APPOINTMENT_ID_PATTERN.matcher(sentence).find()
            || containsAny(normalized, "现在为您创建预约", "为您创建预约", "创建预约");
    }

    private void scheduleTtsSynthesis(Sinks.Many<SseMessageVO> ttsSink,
                                      AtomicInteger pendingTtsTasks,
                                      AtomicBoolean ttsSchedulingComplete,
                                      AtomicReference<ChatMessage> assistantMessageRef,
                                      AtomicReference<String> firstTtsUrlRef,
                                      Long sessionId,
                                      String sentence,
                                      int segmentIndex,
                                      Integer totalSegments,
                                      Map<String, Object> metadata) {
        if (sentence == null || sentence.isBlank()) {
            return;
        }

        pendingTtsTasks.incrementAndGet();
        Mono.fromCallable(() -> buildTtsEvent(sentence, segmentIndex, totalSegments, metadata, sessionId))
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(Duration.ofSeconds(30))
            .subscribe(event -> {
                if (event.getTtsUrl() != null
                    && !event.getTtsUrl().isBlank()
                    && firstTtsUrlRef.compareAndSet(null, event.getTtsUrl())) {
                    ChatMessage assistantMessage = assistantMessageRef.get();
                    if (assistantMessage != null) {
                        assistantMessage.setTtsUrl(event.getTtsUrl());
                        messageMapper.updateById(assistantMessage);
                    }
                }
                ttsSink.tryEmitNext(event);
            }, error -> {
                Tracer.trace(error);
                log.error("TTS 合成失败, sessionId={}: {}", sessionId, error.getMessage(), error);
                SseMessageVO ttsError = new SseMessageVO();
                ttsError.setType("tts_error");
                ttsError.setContent("TTS 超时");
                ttsError.setSegmentIndex(segmentIndex);
                ttsError.setTotalSegments(totalSegments);
                ttsSink.tryEmitNext(ttsError);
            }, () -> {
                if (pendingTtsTasks.decrementAndGet() == 0 && ttsSchedulingComplete.get()) {
                    ttsSink.tryEmitComplete();
                }
            });
    }

    private SseMessageVO buildTtsEvent(String sentence,
                                       int segmentIndex,
                                       Integer totalSegments,
                                       Map<String, Object> metadata,
                                       Long sessionId) {
        String ttsUrl = ttsService.synthesize(sentence);
        if (ttsUrl == null || ttsUrl.isBlank()) {
            SseMessageVO error = new SseMessageVO();
            error.setType("tts_error");
            error.setContent("TTS 合成失败");
            error.setSegmentIndex(segmentIndex);
            error.setTotalSegments(totalSegments);
            return error;
        }

        SseMessageVO tts = new SseMessageVO();
        tts.setType("tts");
        tts.setTtsUrl(ttsUrl);
        tts.setMetadata(metadata);
        tts.setSegmentIndex(segmentIndex);
        tts.setTotalSegments(totalSegments);
        log.info("TTS: 分段下发, sessionId={}, segmentIndex={}, totalSegments={}, text={}",
            sessionId,
            segmentIndex,
            totalSegments,
            sentence.length() > 60 ? sentence.substring(0, 60) + "..." : sentence);
        return tts;
    }

    private String writeMetadataJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.warn("Failed to serialize assistant metadata: {}", e.getMessage());
            return null;
        }
    }

    private String guardAppointmentSuccessReply(Long sessionId, Long userMessageId, Long userId, String userText, String assistantText) {
        if (assistantText == null || assistantText.isBlank()) {
            return assistantText;
        }
        if (!isPotentialFakeSuccessReply(assistantText)) {
            return assistantText;
        }

        List<ChatMessage> toolMessages = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getRole, "tool")
                        .eq(ChatMessage::getToolName, "createAppointment")
                        .gt(userMessageId != null, ChatMessage::getId, userMessageId)
                        .orderByDesc(ChatMessage::getId)
                        .last("LIMIT 3"));

        boolean hasSuccessfulCreate = toolMessages.stream().anyMatch(this::isCreateAppointmentSuccessToolMessage);
        if (hasSuccessfulCreate) {
            return assistantText;
        }

        Long verifiedAppointmentId = findVerifiedAppointmentFromReply(userId, sessionId, assistantText);
        if (verifiedAppointmentId != null) {
            log.info("Guard verified existing appointment from assistant reply, sessionId={}, userMessageId={}, patientId={}, appointmentId={}",
                    sessionId, userMessageId, userId, verifiedAppointmentId);
            return assistantText;
        }

        Long doctorId = extractLongByPattern(assistantText, DOCTOR_ID_PATTERN);
        Long slotId = extractLongByPattern(assistantText, SLOT_ID_PATTERN);

        if (userId != null && doctorId != null) {
            LocalDate date = extractDateFromText(assistantText);
            if (date == null) {
                date = LocalDate.now();
            }

            if (slotId != null && !isSlotAvailableForDoctorOnDate(doctorId, slotId, date)) {
                log.warn("Guard extracted slotId is invalid for doctor/date, sessionId={}, doctorId={}, slotId={}, date={}",
                        sessionId, doctorId, slotId, date);
                slotId = null;
            }

            if (slotId == null) {
                slotId = resolveSlotIdFromUserChoice(doctorId, userText, assistantText, date);
            }
            if (slotId != null) {
                try {
                    Long existingAppointmentId = findAndBindExistingAppointment(userId, slotId, sessionId);
                    if (existingAppointmentId != null) {
                        log.info("Guard found existing appointment, sessionId={}, userMessageId={}, patientId={}, doctorId={}, slotId={}, appointmentId={}",
                                sessionId, userMessageId, userId, doctorId, slotId, existingAppointmentId);
                        return buildAutoCreateSuccessReply(assistantText, existingAppointmentId);
                    }

                    R<Long> createResult = remoteAppointmentService.createAppointment(userId, doctorId, slotId, sessionId);
                    if (createResult != null && createResult.isSuccess() && createResult.getData() != null) {
                        Long appointmentId = createResult.getData();
                        log.info("Guard fallback auto-created appointment, sessionId={}, userMessageId={}, patientId={}, doctorId={}, slotId={}, appointmentId={}",
                                sessionId, userMessageId, userId, doctorId, slotId, appointmentId);
                        return buildAutoCreateSuccessReply(assistantText, appointmentId);
                    }
                    existingAppointmentId = findAndBindExistingAppointment(userId, slotId, sessionId);
                    if (existingAppointmentId != null) {
                        log.info("Guard recovered existing appointment after create failure, sessionId={}, userMessageId={}, patientId={}, doctorId={}, slotId={}, appointmentId={}, result={}",
                                sessionId, userMessageId, userId, doctorId, slotId, existingAppointmentId, createResult);
                        return buildAutoCreateSuccessReply(assistantText, existingAppointmentId);
                    }
                    log.warn("Guard fallback auto-create failed, sessionId={}, userMessageId={}, patientId={}, doctorId={}, slotId={}, result={}",
                            sessionId, userMessageId, userId, doctorId, slotId, createResult);
                } catch (Exception e) {
                    log.error("Guard fallback auto-create exception, sessionId={}, userMessageId={}, patientId={}, doctorId={}, slotId={}",
                            sessionId, userMessageId, userId, doctorId, slotId, e);
                }
            }
        }

        log.warn("Guarded potential fake appointment success reply, sessionId={}, userMessageId={}, assistantText={}",
                sessionId, userMessageId, assistantText);
        return APPOINTMENT_GUARD_FALLBACK_REPLY;
    }

    private Long findVerifiedAppointmentFromReply(Long patientId, Long sessionId, String assistantText) {
        if (patientId == null) {
            return null;
        }
        Long appointmentId = extractLongByPattern(assistantText, APPOINTMENT_ID_VALUE_PATTERN);
        Long verifiedAppointmentId = verifyAppointmentFromReply(patientId, sessionId, appointmentId);
        if (verifiedAppointmentId != null) {
            return verifiedAppointmentId;
        }

        List<Long> candidates = new ArrayList<>();
        Matcher matcher = NUMBER_PATTERN.matcher(assistantText == null ? "" : assistantText);
        while (matcher.find()) {
            try {
                candidates.add(Long.parseLong(matcher.group()));
            } catch (NumberFormatException ignored) {
                // Ignore numbers outside Long range.
            }
        }
        for (int i = candidates.size() - 1; i >= 0; i--) {
            verifiedAppointmentId = verifyAppointmentFromReply(patientId, sessionId, candidates.get(i));
            if (verifiedAppointmentId != null) {
                return verifiedAppointmentId;
            }
        }
        return null;
    }

    private Long verifyAppointmentFromReply(Long patientId, Long sessionId, Long appointmentId) {
        if (appointmentId == null) {
            return null;
        }
        try {
            R<AppointmentDTO> response = remoteAppointmentService.getAppointmentSnapshot(appointmentId);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                return null;
            }
            AppointmentDTO appointment = response.getData();
            if (!Objects.equals(appointment.getPatientId(), patientId)) {
                log.warn("Assistant appointment id belongs to another patient, appointmentId={}, expectedPatientId={}, actualPatientId={}",
                        appointmentId, patientId, appointment.getPatientId());
                return null;
            }
            if (sessionId != null && appointment.getSessionId() == null) {
                R<Void> bindResult = remoteAppointmentService.bindSession(appointmentId, sessionId);
                if (bindResult == null || !bindResult.isSuccess()) {
                    log.warn("Failed to bind assistant appointment id, appointmentId={}, sessionId={}, result={}",
                            appointmentId, sessionId, bindResult);
                    return null;
                }
                return appointmentId;
            }
            if (sessionId != null && !Objects.equals(appointment.getSessionId(), sessionId)) {
                log.warn("Assistant appointment id belongs to another session, appointmentId={}, expectedSessionId={}, actualSessionId={}",
                        appointmentId, sessionId, appointment.getSessionId());
                return null;
            }
            return appointmentId;
        } catch (Exception e) {
            log.warn("Failed to verify assistant appointment id, appointmentId={}, patientId={}, sessionId={}, error={}",
                    appointmentId, patientId, sessionId, e.getMessage());
            return null;
        }
    }

    private Long findAndBindExistingAppointment(Long patientId, Long slotId, Long sessionId) {
        if (patientId == null || slotId == null) {
            return null;
        }
        try {
            R<AppointmentDTO> response = remoteAppointmentService.getAppointmentByPatientAndSlot(patientId, slotId);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                return null;
            }
            AppointmentDTO appointment = response.getData();
            if (sessionId != null && appointment.getId() != null && appointment.getSessionId() == null) {
                R<Void> bindResult = remoteAppointmentService.bindSession(appointment.getId(), sessionId);
                if (bindResult == null || !bindResult.isSuccess()) {
                    log.warn("Failed to bind appointment session, appointmentId={}, sessionId={}, result={}",
                            appointment.getId(), sessionId, bindResult);
                }
            }
            return appointment.getId();
        } catch (Exception e) {
            log.warn("Failed to find existing appointment, patientId={}, slotId={}, sessionId={}, error={}",
                    patientId, slotId, sessionId, e.getMessage());
            return null;
        }
    }

    private boolean isPotentialFakeSuccessReply(String text) {
        return APPOINTMENT_SUCCESS_PATTERN.matcher(text).find() || APPOINTMENT_ID_PATTERN.matcher(text).find();
    }

    private boolean isCreateAppointmentSuccessToolMessage(ChatMessage toolMessage) {
        if (toolMessage == null || toolMessage.getContent() == null || toolMessage.getContent().isBlank()) {
            return false;
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(
                    toolMessage.getContent(),
                    new TypeReference<Map<String, Object>>() {
                    });
            Object successObj = payload.get("success");
            Object appointmentIdObj = payload.get("appointmentId");
            boolean success = successObj instanceof Boolean ? (Boolean) successObj : "true".equals(String.valueOf(successObj));
            if (!success || appointmentIdObj == null) {
                return false;
            }
            String appointmentId = String.valueOf(appointmentIdObj).trim();
            return !appointmentId.isEmpty() && !"null".equalsIgnoreCase(appointmentId);
        } catch (Exception e) {
            log.warn("Failed to parse createAppointment tool message, id={}, content={}",
                    toolMessage.getId(), toolMessage.getContent());
            return false;
        }
    }

    private Long extractLongByPattern(String text, Pattern pattern) {
        if (text == null || pattern == null) {
            return null;
        }
        var matcher = pattern.matcher(text);
        if (!matcher.find() || matcher.groupCount() < 1) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String buildAutoCreateSuccessReply(String originalText, Long appointmentId) {
        String merged = originalText.replaceAll("(?i)appointmentId\\s*[:：]?\\s*\\d+", "appointmentId: " + appointmentId)
                .replaceAll("预约ID\\s*[:：]?\\s*\\d+", "预约ID：" + appointmentId);
        if (merged.contains("预约ID")) {
            return merged;
        }
        return merged + "\n\n预约ID：" + appointmentId;
    }

    private Long resolveSlotIdFromUserChoice(Long doctorId, String userText, String assistantText, LocalDate date) {
        if (doctorId == null) {
            return null;
        }

        String targetPeriod = resolveTargetPeriod(userText, assistantText);
        if (targetPeriod.isBlank()) {
            return null;
        }

        LocalDate queryDate = date != null ? date : extractDateFromText(assistantText);
        if (queryDate == null) {
            queryDate = LocalDate.now();
        }

        R<List<SlotInfoDTO>> slotsResp = remoteScheduleService.getAvailableSlots(doctorId, queryDate.toString());
        if (slotsResp == null || !slotsResp.isSuccess() || slotsResp.getData() == null || slotsResp.getData().isEmpty()) {
            return null;
        }

        return slotsResp.getData().stream()
                .filter(Objects::nonNull)
                .filter(slot -> Objects.equals(slot.getDoctorId(), doctorId))
                .filter(slot -> targetPeriod.equalsIgnoreCase(String.valueOf(slot.getPeriod())))
                .sorted(Comparator.comparing(SlotInfoDTO::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(SlotInfoDTO::getId)
                .findFirst()
                .orElse(null);
    }

    private boolean isSlotAvailableForDoctorOnDate(Long doctorId, Long slotId, LocalDate date) {
        if (doctorId == null || slotId == null || date == null) {
            return false;
        }
        R<List<SlotInfoDTO>> slotsResp = remoteScheduleService.getAvailableSlots(doctorId, date.toString());
        if (slotsResp == null || !slotsResp.isSuccess() || slotsResp.getData() == null || slotsResp.getData().isEmpty()) {
            return false;
        }
        return slotsResp.getData().stream()
                .filter(Objects::nonNull)
                .anyMatch(slot -> Objects.equals(slot.getId(), slotId) && Objects.equals(slot.getDoctorId(), doctorId));
    }

    private String normalizeChoice(String userText) {
        if (userText == null) {
            return "";
        }
        return userText.replaceAll("\\s+", "").trim();
    }

    private String mapChoiceToPeriod(String choice) {
        if (choice == null || choice.isBlank()) {
            return "";
        }
        if (choice.contains("上午") || choice.contains("早上") || choice.contains("morning")) {
            return "morning";
        }
        if (choice.contains("下午") || choice.contains("晚上") || choice.contains("afternoon")) {
            return "afternoon";
        }
        return "";
    }

    private String resolveTargetPeriod(String userText, String assistantText) {
        String fromUser = mapChoiceToPeriod(normalizeChoice(userText));
        if (!fromUser.isBlank()) {
            return fromUser;
        }

        String fromAssistant = extractPeriodFromAssistant(assistantText);
        return mapChoiceToPeriod(fromAssistant);
    }

    private String extractPeriodFromAssistant(String assistantText) {
        if (assistantText == null || assistantText.isBlank()) {
            return "";
        }

        Matcher confirmMatcher = CONFIRM_PERIOD_PATTERN.matcher(assistantText);
        if (confirmMatcher.find()) {
            return confirmMatcher.group(1);
        }

        Matcher appointmentMatcher = APPOINTMENT_PERIOD_PATTERN.matcher(assistantText);
        if (appointmentMatcher.find()) {
            return appointmentMatcher.group(1);
        }

        Matcher simpleMatcher = SIMPLE_PERIOD_PATTERN.matcher(assistantText);
        String last = "";
        while (simpleMatcher.find()) {
            last = simpleMatcher.group(1);
        }
        return last;
    }

    private LocalDate extractDateFromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = DATE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }
}
