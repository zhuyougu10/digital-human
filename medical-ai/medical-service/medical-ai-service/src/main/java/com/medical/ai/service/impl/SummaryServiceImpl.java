package com.medical.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.ai.agent.Agent;
import com.medical.ai.agent.AgentFactory;
import com.medical.ai.domain.entity.ChatMessage;
import com.medical.ai.domain.entity.ChatSession;
import com.medical.ai.domain.entity.ConversationSummary;
import com.medical.ai.domain.vo.ConversationSummaryVO;
import com.medical.ai.mapper.ChatMessageMapper;
import com.medical.ai.mapper.ChatSessionMapper;
import com.medical.ai.mapper.ConversationSummaryMapper;
import com.medical.ai.service.SummaryService;
import com.medical.api.appointment.RemoteAppointmentService;
import com.medical.api.appointment.dto.AppointmentDTO;
import com.medical.api.doctor.RemoteDoctorService;
import com.medical.api.doctor.dto.DoctorInfoDTO;
import com.medical.common.core.constant.UserConstants;
import com.medical.common.core.domain.R;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import java.util.Collections;
import java.util.LinkedHashSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryServiceImpl implements SummaryService {

    private static final String NOT_MENTIONED = "未提及";
    private static final String NO_HISTORY = "无特殊既往史";
    private static final List<String> SYMPTOM_KEYWORDS = List.of(
            "感冒", "咳嗽", "发热", "发烧", "嗓子痛", "咽痛", "头痛", "头疼", "头晕", "眩晕",
            "胸痛", "胸闷", "腹痛", "胃痛", "皮疹", "失眠", "心悸", "鼻塞", "流涕", "恶心", "呕吐");
    private static final List<String> BODY_LOCATION_KEYWORDS = List.of(
            "头", "额头", "太阳穴", "后脑勺", "眼睛", "鼻", "鼻子", "咽", "咽喉", "喉咙", "嗓子",
            "胸", "胸口", "心口", "腹", "肚子", "胃", "腰", "背", "皮肤", "四肢", "关节");
    private static final Pattern DURATION_PATTERN = Pattern.compile("([0-9一二两三四五六七八九十半]+\\s*(天|日|周|星期|月|小时|分钟))|今天|昨天|刚开始|好几天|一周|半天|昨晚");

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ConversationSummaryMapper summaryMapper;
    private final AgentFactory agentFactory;
    private final OpenAiChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final RemoteAppointmentService remoteAppointmentService;
    private final RemoteDoctorService remoteDoctorService;

    @Async("summaryExecutor")
    @Override
    public void generateSummary(Long sessionId, Long appointmentId) {
        try {
            ConversationSummary existing = summaryMapper.selectOne(
                new LambdaQueryWrapper<ConversationSummary>()
                    .eq(ConversationSummary::getSessionId, sessionId)
            );
            if (existing != null) {
                if (appointmentId != null && !appointmentId.equals(existing.getAppointmentId())) {
                    existing.setAppointmentId(appointmentId);
                    summaryMapper.updateById(existing);
                }
                ChatSession session = sessionMapper.selectById(sessionId);
                if (session != null) {
                    session.setStatus(2);
                    sessionMapper.updateById(session);
                }
                log.info("Summary already exists for session {}", sessionId);
                return;
            }

            ChatSession session = sessionMapper.selectById(sessionId);
            if (session == null) {
                return;
            }

            List<ChatMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getSessionId, sessionId)
                    .orderByAsc(ChatMessage::getCreateTime)
            );
            if (messages.isEmpty()) {
                return;
            }

            String conversationText = messages.stream()
                .filter(m -> "user".equals(m.getRole()) || "assistant".equals(m.getRole()))
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

            Agent summaryAgent = agentFactory.getAgent("SUMMARY");
            List<Message> chatMessages = new ArrayList<>();
            chatMessages.add(new SystemMessage(summaryAgent.getSystemPrompt()));
            chatMessages.add(new UserMessage(conversationText));

            Prompt prompt = new Prompt(chatMessages);
            String response = chatModel.call(prompt).getResult().getOutput().getContent();
            if (response == null) {
                response = "";
            }

            ConversationSummary summary = new ConversationSummary();
            summary.setSessionId(sessionId);
            summary.setUserId(session.getUserId());
            summary.setAppointmentId(appointmentId);
            summary.setFullSummary(response);

            try {
                JsonNode json = objectMapper.readTree(response);
                summary.setChiefComplaint(getJsonField(json, "chiefComplaint"));
                summary.setSymptoms(getJsonField(json, "symptoms"));
                summary.setDuration(getJsonField(json, "duration"));
                summary.setSeverity(getJsonField(json, "severity"));
                summary.setMedicalHistory(getJsonField(json, "medicalHistory"));
                summary.setAiAssessment(getJsonField(json, "aiAssessment"));
            } catch (Exception e) {
                log.warn("Failed to parse summary JSON, saving raw text: {}", e.getMessage());
            }

            summaryMapper.insert(summary);

            session.setStatus(2);
            sessionMapper.updateById(session);
            log.info("Summary generated for session {}", sessionId);
        } catch (Exception e) {
            log.error("Failed to generate summary for session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    @Override
    public ConversationSummaryVO syncTriageSummary(Long sessionId, Long userId, Long appointmentId, List<ChatMessage> messages) {
        ConversationSummary extracted = extractStructuredTriageSummary(sessionId, userId, appointmentId, messages);
        ConversationSummary existing = summaryMapper.selectOne(
                new LambdaQueryWrapper<ConversationSummary>()
                        .eq(ConversationSummary::getSessionId, sessionId)
        );
        if (existing == null) {
            summaryMapper.insert(extracted);
            return toVO(extracted);
        }

        existing.setUserId(userId);
        if (appointmentId != null) {
            existing.setAppointmentId(appointmentId);
        }
        existing.setChiefComplaint(extracted.getChiefComplaint());
        existing.setSymptoms(extracted.getSymptoms());
        existing.setDuration(extracted.getDuration());
        existing.setSeverity(extracted.getSeverity());
        existing.setMedicalHistory(extracted.getMedicalHistory());
        existing.setAiAssessment(extracted.getAiAssessment());
        existing.setFullSummary(extracted.getFullSummary());
        summaryMapper.updateById(existing);
        return toVO(existing);
    }

    @Override
    public ConversationSummaryVO getSummaryBySession(Long sessionId, Long userId) {
        validateSessionOwner(sessionId, userId);
        ConversationSummary summary = summaryMapper.selectOne(
            new LambdaQueryWrapper<ConversationSummary>()
                .eq(ConversationSummary::getSessionId, sessionId)
        );
        return summary != null ? toVO(summary) : null;
    }

    @Override
    public ConversationSummaryVO getSummaryByAppointment(Long appointmentId, Long userId, List<String> roles) {
        AppointmentDTO appointment = loadAuthorizedAppointment(appointmentId, userId, roles);
        ConversationSummary summary = summaryMapper.selectOne(
            new LambdaQueryWrapper<ConversationSummary>()
                .eq(ConversationSummary::getAppointmentId, appointment.getId())
        );
        return summary != null ? toVO(summary) : null;
    }

    private AppointmentDTO loadAuthorizedAppointment(Long appointmentId, Long userId, List<String> roles) {
        R<AppointmentDTO> appointmentResponse = remoteAppointmentService.getAppointmentSnapshot(appointmentId);
        if (appointmentResponse == null || !appointmentResponse.isSuccess() || appointmentResponse.getData() == null) {
            throw new BusinessException(ErrorCode.APPOINTMENT_NOT_FOUND);
        }
        AppointmentDTO appointment = appointmentResponse.getData();
        List<String> safeRoles = roles == null ? Collections.emptyList() : roles;
        if (safeRoles.contains(UserConstants.ROLE_ADMIN)) {
            return appointment;
        }
        if (userId != null && userId.equals(appointment.getPatientId())) {
            return appointment;
        }
        if (safeRoles.contains(UserConstants.ROLE_DOCTOR)) {
            R<DoctorInfoDTO> doctorResponse = remoteDoctorService.getDoctorByUserId(userId);
            if (doctorResponse != null && doctorResponse.isSuccess() && doctorResponse.getData() != null) {
                DoctorInfoDTO doctor = doctorResponse.getData();
                if (doctor.getId() != null && doctor.getId().equals(appointment.getDoctorId())) {
                    return appointment;
                }
            }
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private void validateSessionOwner(Long sessionId, Long userId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!userId.equals(session.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private String getJsonField(JsonNode json, String field) {
        JsonNode node = json.get(field);
        return node != null ? node.asText() : null;
    }

    private ConversationSummary extractStructuredTriageSummary(Long sessionId, Long userId, Long appointmentId, List<ChatMessage> messages) {
        List<String> userMessages = messages == null ? List.of() : messages.stream()
                .filter(message -> message != null && "user".equals(message.getRole()))
                .map(ChatMessage::getContent)
                .filter(Objects::nonNull)
                .filter(content -> !content.isBlank())
                .toList();
        String allUserText = String.join("\n", userMessages);

        ConversationSummary summary = new ConversationSummary();
        summary.setSessionId(sessionId);
        summary.setUserId(userId);
        summary.setAppointmentId(appointmentId);
        summary.setChiefComplaint(resolveChiefComplaint(allUserText));
        summary.setSymptoms(resolveAssociatedSymptoms(allUserText, summary.getChiefComplaint()));
        summary.setDuration(resolveDuration(allUserText));
        summary.setSeverity(resolveSeverity(allUserText));
        summary.setMedicalHistory(resolveMedicalHistory(allUserText));
        summary.setAiAssessment(resolveAiAssessment(summary));
        summary.setFullSummary(writeStructuredSummary(summary));
        return summary;
    }

    private String resolveChiefComplaint(String text) {
        if (text == null || text.isBlank()) {
            return NOT_MENTIONED;
        }
        for (String keyword : SYMPTOM_KEYWORDS) {
            if (text.contains(keyword)) {
                return keyword;
            }
        }
        if (text.contains("不舒服")) {
            return "不舒服";
        }
        if (text.contains("疼") || text.contains("痛")) {
            return "疼痛不适";
        }
        return NOT_MENTIONED;
    }

    private String resolveAssociatedSymptoms(String text, String chiefComplaint) {
        if (text == null || text.isBlank()) {
            return NOT_MENTIONED;
        }
        Set<String> symptoms = new LinkedHashSet<>();
        List<String> locations = BODY_LOCATION_KEYWORDS.stream()
                .filter(text::contains)
                .distinct()
                .toList();
        if (!locations.isEmpty()) {
            symptoms.add("部位：" + String.join("、", locations));
        }
        for (String keyword : SYMPTOM_KEYWORDS) {
            if (text.contains(keyword) && !keyword.equals(chiefComplaint)) {
                symptoms.add(keyword);
            }
        }
        if (containsAny(text, "胸闷", "气短", "呼吸困难")) {
            symptoms.add("胸闷气短");
        }
        if (containsAny(text, "没有其他症状", "无其他症状", "不伴", "没有其他不舒服")) {
            symptoms.add("无其他明显伴随症状");
        }
        if (containsAny(text, "伴有", "还有", "同时") && symptoms.isEmpty()) {
            return "有伴随不适，具体症状需进一步确认";
        }
        return symptoms.isEmpty() ? NOT_MENTIONED : String.join("、", symptoms);
    }

    private String resolveDuration(String text) {
        Matcher matcher = DURATION_PATTERN.matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group() : NOT_MENTIONED;
    }

    private String resolveSeverity(String text) {
        if (text == null || text.isBlank()) {
            return NOT_MENTIONED;
        }
        if (containsAny(text, "严重", "剧烈", "很痛", "不能", "明显影响", "比较严重")) {
            return "较重";
        }
        if (containsAny(text, "中等", "一般", "中等不适")) {
            return "中等";
        }
        if (containsAny(text, "轻微", "较轻", "不严重", "可忍受", "还好", "不影响")) {
            return "较轻";
        }
        return NOT_MENTIONED;
    }

    private String resolveMedicalHistory(String text) {
        if (text == null || text.isBlank()) {
            return NOT_MENTIONED;
        }
        if (containsAny(text, "无既往史", "没有既往史", "无基础病", "没有基础病", "无过敏史", "没有过敏史", "没有长期用药")) {
            return NO_HISTORY;
        }
        List<String> histories = new ArrayList<>();
        if (containsAny(text, "高血压")) {
            histories.add("高血压");
        }
        if (containsAny(text, "糖尿病")) {
            histories.add("糖尿病");
        }
        if (containsAny(text, "冠心病", "心脏病")) {
            histories.add("心血管病史");
        }
        if (containsAny(text, "哮喘")) {
            histories.add("哮喘");
        }
        if (containsAny(text, "过敏")) {
            histories.add("过敏史");
        }
        if (containsAny(text, "长期用药")) {
            histories.add("长期用药史");
        }
        return histories.isEmpty() ? NOT_MENTIONED : String.join("、", histories);
    }

    private String resolveAiAssessment(ConversationSummary summary) {
        if (isMissing(summary.getChiefComplaint())) {
            return "当前主诉信息不足，需先补充主要不适。";
        }
        List<String> missing = new ArrayList<>();
        if (isMissing(summary.getDuration())) {
            missing.add("持续时间");
        }
        if (isMissing(summary.getSymptoms())) {
            missing.add("伴随症状");
        }
        if (isMissing(summary.getSeverity())) {
            missing.add("严重程度");
        }
        if (isMissing(summary.getMedicalHistory())) {
            missing.add("既往史");
        }
        if (!missing.isEmpty()) {
            return "已记录主诉，仍需补充" + String.join("、", missing) + "后再进入挂号。";
        }
        return buildClinicalAssessment(summary);
    }

    private String buildClinicalAssessment(ConversationSummary summary) {
        String symptomText = Objects.toString(summary.getChiefComplaint(), "")
                + " " + Objects.toString(summary.getSymptoms(), "");
        String suspectedCondition;
        if (containsAny(symptomText, "发热", "发烧", "咳嗽", "嗓子痛", "咽痛", "喉咙", "鼻塞", "流涕", "感冒")) {
            suspectedCondition = "上呼吸道感染、感冒或咽喉炎相关问题";
        } else if (containsAny(symptomText, "头痛", "头疼", "头晕", "眩晕")) {
            suspectedCondition = "偏头痛、紧张性头痛或其他头痛相关问题";
        } else if (containsAny(symptomText, "腹痛", "胃痛", "恶心", "呕吐", "腹泻")) {
            suspectedCondition = "胃肠道不适或消化系统相关问题";
        } else if (containsAny(symptomText, "皮疹", "湿疹", "痤疮")) {
            suspectedCondition = "皮肤炎症或皮肤过敏相关问题";
        } else if (containsAny(symptomText, "胸痛", "胸闷", "心悸")) {
            suspectedCondition = "胸部不适或心肺相关问题";
        } else {
            suspectedCondition = "当前症状相关疾病";
        }
        return "疑似" + suspectedCondition + "，建议结合体温、症状变化和医生面诊进一步确认；如症状加重或出现急症表现，应及时线下就医。";
    }

    private String writeStructuredSummary(ConversationSummary summary) {
        return "{"
                + "\"chiefComplaint\":\"" + escapeJson(summary.getChiefComplaint()) + "\","
                + "\"symptoms\":\"" + escapeJson(summary.getSymptoms()) + "\","
                + "\"duration\":\"" + escapeJson(summary.getDuration()) + "\","
                + "\"severity\":\"" + escapeJson(summary.getSeverity()) + "\","
                + "\"medicalHistory\":\"" + escapeJson(summary.getMedicalHistory()) + "\","
                + "\"aiAssessment\":\"" + escapeJson(summary.getAiAssessment()) + "\""
                + "}";
    }

    private boolean isMissing(String value) {
        return value == null || value.isBlank() || NOT_MENTIONED.equals(value) || "-".equals(value);
    }

    private boolean containsAny(String text, String... needles) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String escapeJson(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private ConversationSummaryVO toVO(ConversationSummary summary) {
        ConversationSummaryVO vo = new ConversationSummaryVO();
        vo.setId(summary.getId());
        vo.setSessionId(summary.getSessionId());
        vo.setUserId(summary.getUserId());
        vo.setAppointmentId(summary.getAppointmentId());
        vo.setChiefComplaint(summary.getChiefComplaint());
        vo.setSymptoms(summary.getSymptoms());
        vo.setDuration(summary.getDuration());
        vo.setSeverity(summary.getSeverity());
        vo.setMedicalHistory(summary.getMedicalHistory());
        vo.setAiAssessment(summary.getAiAssessment());
        vo.setFullSummary(summary.getFullSummary());
        vo.setCreateTime(summary.getCreateTime());
        return vo;
    }
}
