package com.medical.ai.service.impl;

import com.medical.ai.domain.entity.ChatMessage;
import com.medical.ai.domain.vo.ConversationSummaryVO;
import com.medical.api.appointment.RemoteAppointmentService;
import com.medical.api.appointment.dto.AppointmentDTO;
import com.medical.api.doctor.RemoteDoctorService;
import com.medical.api.doctor.RemoteScheduleService;
import com.medical.api.doctor.dto.DoctorInfoDTO;
import com.medical.api.doctor.dto.SlotInfoDTO;
import com.medical.api.knowledge.RemoteKnowledgeService;
import com.medical.api.knowledge.dto.KnowledgeSearchRequest;
import com.medical.api.knowledge.dto.KnowledgeSearchResult;
import com.medical.common.core.domain.R;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TriageAppointmentFlowService {

    private static final ZoneId SERVER_ZONE = ZoneId.systemDefault();
    private static final Pattern DATE_CN_PATTERN = Pattern.compile("(20\\d{2})年(\\d{1,2})月(\\d{1,2})日");
    private static final Pattern DATE_DASH_PATTERN = Pattern.compile("(20\\d{2})[-/](\\d{1,2})[-/](\\d{1,2})");
    private static final Pattern DOCTOR_ID_PATTERN = Pattern.compile("doctorId\\s*[:：=]?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SLOT_ID_PATTERN = Pattern.compile("slotId\\s*[:：=]?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern OPTION_SELECTION_PATTERN = Pattern.compile("(?:选|选择|第)\\s*(\\d+)\\s*(?:个|位|名)?");
    private static final Pattern DOCTOR_OPTION_PATTERN = Pattern.compile("(\\d+)\\.\\s*([^\\n（(]+).*?doctorId=(\\d+)");
    private static final Pattern DOCTOR_OPTION_DISPLAY_PATTERN = Pattern.compile("(\\d+)\\.\\s*([^\\n（(]+)");
    private static final Pattern CONFIRMATION_DOCTOR_PATTERN = Pattern.compile("医生[：:]\\s*([^（\\n]+)");
    private static final List<String> SYMPTOM_KEYWORDS = List.of(
            "咳嗽", "发热", "发烧", "感冒", "嗓子痛", "咽痛", "头痛", "头疼", "头晕", "眩晕",
            "胸痛", "胸闷", "腹痛", "胃痛", "皮疹", "失眠", "心悸", "鼻塞", "流涕", "恶心");
    private static final String DISCLAIMER = "\n\nAI导诊仅供参考，不能替代专业医生诊断。";

    private final RemoteDoctorService remoteDoctorService;
    private final RemoteScheduleService remoteScheduleService;
    private final RemoteAppointmentService remoteAppointmentService;
    private final RemoteKnowledgeService remoteKnowledgeService;

    public TriageFlowResult handle(Long sessionId, Long patientId, List<ChatMessage> messages, ConversationSummaryVO summary) {
        FlowContext context = FlowContext.from(messages);
        TriageInfo triageInfo = extractTriageInfo(context.allUserText(), summary);
        if (!triageInfo.hasSymptom()) {
            return new TriageFlowResult("""
                    我先了解一下您的主要不舒服，再帮您判断适合预约哪类医生。

                    请您描述一下主要症状，比如哪里不舒服、有什么表现？""".strip() + DISCLAIMER,
                    null,
                    List.of("咳嗽发热", "头痛头晕", "腹痛恶心"));
        }
        if (!triageInfo.hasDuration()) {
            return new TriageFlowResult("我了解了您的主要不舒服。为了判断就诊紧急程度，请问这些症状持续多久了？"
                    + DISCLAIMER,
                    null,
                    List.of("今天刚开始", "已经三天了", "一周左右"));
        }
        if (!triageInfo.hasAssociatedInfo()) {
            return new TriageFlowResult("还需要确认一下伴随情况：有没有发热、胸闷气短、剧烈疼痛、呕吐，或其他明显不舒服？"
                    + DISCLAIMER,
                    null,
                    List.of("伴有发烧", "没有其他症状", "有胸闷气短"));
        }
        if (!triageInfo.hasSeverityInfo()) {
            return new TriageFlowResult("我再确认一下严重程度：目前症状是轻微、中等，还是很严重？有没有影响进食、睡眠或日常活动？"
                    + DISCLAIMER,
                    null,
                    List.of("症状较轻", "中等不适", "比较严重"));
        }
        if (!triageInfo.hasMedicalHistory()) {
            return new TriageFlowResult("还需要补充既往史：您是否有基础病、过敏史、长期用药，或以前相关疾病史？"
                    + DISCLAIMER,
                    null,
                    List.of("没有基础病和过敏史", "有高血压", "有药物过敏史"));
        }

        if (!hasCareDecisionPrompt(context)) {
            return new TriageFlowResult(buildCareDecisionPrompt(context, summary),
                    null,
                    List.of("需要就医", "暂时先观察", "帮我预约"));
        }
        if (declinesMedicalCare(context.latestUserText())) {
            return new TriageFlowResult("""
                    好的，您可以先观察病情变化，注意休息和补水。

                    如果症状加重、持续不缓解，或出现高热、胸闷气短、剧烈疼痛等情况，请及时就医。""".strip() + DISCLAIMER,
                    null,
                    List.of("我想预约", "再问问症状", "结束"));
        }
        if (!wantsMedicalCare(context.latestUserText()) && !isAppointmentFlowInProgress(context)) {
            return new TriageFlowResult("您是否需要我继续帮您预约医生就诊？"
                    + DISCLAIMER,
                    null,
                    List.of("需要就医", "暂时先观察", "帮我预约"));
        }

        AppointmentTime appointmentTime = extractAppointmentTime(context.allUserText());
        if (appointmentTime == null) {
            return new TriageFlowResult("""
                    好的，我继续帮您预约。请告诉我想预约的日期和时段。

                    例如：2026年5月25日上午，或 2026-05-25 下午。""".strip() + DISCLAIMER,
                    null,
                    List.of("2026年5月25日上午", "2026-05-25 下午", "明天上午"));
        }

        SelectedDoctor selectedDoctor = resolveSelectedDoctor(context);
        if (selectedDoctor == null) {
            return buildDoctorSelectionResult(context, appointmentTime);
        }

        SlotInfoDTO selectedSlot = resolveSelectedSlot(context, selectedDoctor, appointmentTime);
        if (selectedSlot == null) {
            return new TriageFlowResult(buildSlotSelectionReply(selectedDoctor, appointmentTime), null);
        }

        if (!isConfirming(context.latestUserText())) {
            return new TriageFlowResult(buildAppointmentConfirmationReply(selectedDoctor, selectedSlot),
                    null,
                    List.of("确认预约", "我再看看"));
        }

        Long appointmentId = createOrReuseAppointment(patientId, selectedDoctor.doctorId(), selectedSlot.getId(), sessionId);
        if (appointmentId == null) {
            return new TriageFlowResult("""
                    抱歉，这次没有成功提交预约。

                    您可以稍后再确认一次预约，或换一个医生/时间段，我再帮您处理。""".strip() + DISCLAIMER,
                    null,
                    List.of("确认预约", "换个时间", "重新选择医生"));
        }
        return new TriageFlowResult(buildAppointmentSuccessReply(selectedDoctor, selectedSlot, appointmentId), appointmentId);
    }

    private String buildCareDecisionPrompt(FlowContext context, ConversationSummaryVO summary) {
        String query = buildKnowledgeQuery(context.allUserText(), summary);
        String assessment = searchKnowledgeAssessment(query);
        if (!hasSummaryValue(assessment)) {
            assessment = summary == null ? null : summary.getAiAssessment();
        }
        if (!hasSummaryValue(assessment) || isGenericAssessment(assessment)) {
            assessment = "疑似与" + suspectedCondition(query) + "相关，建议结合后续变化和医生面诊进一步确认。";
        }
        return """
                目前信息基本完整。结合您的描述和知识库资料，我的初步判断是：

                %s

                您需要我继续帮您预约医生就诊吗？""".strip().formatted(assessment) + DISCLAIMER;
    }

    private String buildKnowledgeQuery(String userText, ConversationSummaryVO summary) {
        StringBuilder query = new StringBuilder();
        if (summary != null) {
            appendIfSummaryValue(query, summary.getChiefComplaint());
            appendIfSummaryValue(query, summary.getSymptoms());
            appendIfSummaryValue(query, summary.getDuration());
            appendIfSummaryValue(query, summary.getSeverity());
            appendIfSummaryValue(query, summary.getMedicalHistory());
        }
        if (query.isEmpty() && userText != null) {
            query.append(userText);
        }
        return query.toString().trim();
    }

    private void appendIfSummaryValue(StringBuilder target, String value) {
        if (hasSummaryValue(value)) {
            if (!target.isEmpty()) {
                target.append(' ');
            }
            target.append(value);
        }
    }

    private String searchKnowledgeAssessment(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        KnowledgeSearchRequest request = new KnowledgeSearchRequest();
        request.setQuery(query);
        request.setTopK(3);
        try {
            R<List<KnowledgeSearchResult>> response = remoteKnowledgeService.search(request);
            if (response == null || !response.isSuccess() || response.getData() == null || response.getData().isEmpty()) {
                return null;
            }
            KnowledgeSearchResult result = response.getData().stream()
                    .filter(Objects::nonNull)
                    .filter(item -> item.getContent() != null && !item.getContent().isBlank())
                    .findFirst()
                    .orElse(null);
            if (result == null) {
                return null;
            }
            String content = compactText(result.getContent(), 90);
            String condition = suspectedCondition(query + " " + result.getContent());
            return "疑似与" + condition + "相关。知识库中相近资料提示：" + content + "。建议由医生面诊确认。";
        } catch (Exception ex) {
            log.warn("Failed to search knowledge before triage care decision, query={}", query, ex);
            return null;
        }
    }

    private String compactText(String text, int maxLength) {
        String compacted = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (compacted.length() <= maxLength) {
            return compacted;
        }
        return compacted.substring(0, maxLength) + "...";
    }

    private boolean isGenericAssessment(String assessment) {
        return containsAny(assessment, "已记录主诉", "继续补充结构化病情信息", "AI判断", "未提及");
    }

    private String suspectedCondition(String text) {
        String combined = text == null ? "" : text;
        if (containsAny(combined, "咳嗽", "发热", "发烧", "感冒", "嗓子痛", "咽痛", "鼻塞", "流涕", "上呼吸道")) {
            return "上呼吸道感染或感冒相关问题";
        }
        if (containsAny(combined, "头痛", "头疼", "头晕", "眩晕")) {
            return "头痛头晕相关问题";
        }
        if (containsAny(combined, "腹痛", "胃痛", "恶心", "呕吐", "腹泻")) {
            return "消化道不适相关问题";
        }
        if (containsAny(combined, "皮疹", "湿疹", "过敏", "痤疮")) {
            return "皮肤或过敏相关问题";
        }
        return "当前症状相关疾病";
    }

    private boolean hasCareDecisionPrompt(FlowContext context) {
        return containsAny(context.allAssistantText(), "初步判断", "疑似", "需要我继续帮您预约医生就诊", "需要我继续帮您预约");
    }

    private boolean wantsMedicalCare(String text) {
        return containsAny(text, "需要就医", "帮我预约", "我要预约", "想预约", "预约医生", "挂号", "看医生", "继续预约");
    }

    private boolean declinesMedicalCare(String text) {
        return containsAny(text, "暂时先观察", "先观察", "不用", "不需要", "暂时不用", "先不预约", "不预约");
    }

    private boolean isAppointmentFlowInProgress(FlowContext context) {
        return extractAppointmentTime(context.allUserText()) != null
                || containsAny(context.allAssistantText(), "请回复序号", "请选择医生", "已为您选定医生", "确认预约", "就诊时间")
                || OPTION_SELECTION_PATTERN.matcher(context.latestUserText()).find()
                || isConfirming(context.latestUserText());
    }

    private TriageFlowResult buildDoctorSelectionResult(FlowContext context, AppointmentTime appointmentTime) {
        List<DoctorCandidate> candidates = findAvailableDoctors(context.allUserText(), appointmentTime);
        if (candidates.isEmpty()) {
            return new TriageFlowResult("您想预约的时间是：" + appointmentTime.display() + "。\n\n"
                    + "我暂时没有找到该时段有可用号源的匹配医生。请换一个日期或时段，我再帮您查。"
                    + DISCLAIMER,
                    null,
                    List.of("明天上午", "后天下午", "换个时间"));
        }

        StringBuilder reply = new StringBuilder();
        reply.append("您想预约的时间是：").append(appointmentTime.display()).append("。\n\n");
        reply.append("我按您的症状和该时间段查到了这些可预约医生，请回复序号或医生姓名选择：\n");
        for (int i = 0; i < candidates.size(); i++) {
            DoctorCandidate candidate = candidates.get(i);
            reply.append(i + 1)
                    .append(". ")
                    .append(candidate.doctor().getName())
                    .append("（")
                    .append(nullToDash(candidate.doctor().getDepartmentNames()))
                    .append("，")
                    .append(nullToDash(candidate.doctor().getTitle()))
                    .append("，挂号费=")
                    .append(candidate.doctor().getConsultationFee() == null ? "系统当前显示挂号费为0元" : candidate.doctor().getConsultationFee() + "元")
                    .append("）\n");
        }
        reply.append("\n请回复“选1”或医生姓名。").append(DISCLAIMER);
        List<String> suggestedReplies = new ArrayList<>();
        for (int i = 0; i < candidates.size() && i < 3; i++) {
            suggestedReplies.add("选" + (i + 1));
        }
        return new TriageFlowResult(reply.toString(), null, suggestedReplies);
    }

    private String buildSlotSelectionReply(SelectedDoctor selectedDoctor, AppointmentTime appointmentTime) {
        List<SlotInfoDTO> slots = getAvailableSlots(selectedDoctor.doctorId(), appointmentTime.date()).stream()
                .filter(slot -> appointmentTime.period().equals(slot.getPeriod()))
                .filter(this::hasCapacity)
                .sorted(Comparator.comparing(SlotInfoDTO::getStartTime))
                .toList();
        if (slots.isEmpty()) {
            return selectedDoctor.name() + "医生在 " + appointmentTime.display()
                    + " 暂时没有可预约号源。请换一个时间，我再帮您查。" + DISCLAIMER;
        }
        SlotInfoDTO slot = slots.get(0);
        return buildAppointmentConfirmationReply(selectedDoctor, slot);
    }

    private String buildAppointmentConfirmationReply(SelectedDoctor selectedDoctor, SlotInfoDTO slot) {
        return "已为您选定医生和时间：\n\n"
                + "- 医生：" + selectedDoctor.name() + "\n"
                + "- 就诊时间：" + slot.getScheduleDate() + " " + displayPeriod(slot.getPeriod())
                + " " + slot.getStartTime() + "-" + slot.getEndTime() + "\n"
                + "- 剩余号源：" + slot.getAvailableSlots() + " 个\n\n"
                + "请回复“确认预约”，我就为您创建预约。" + DISCLAIMER;
    }

    private String buildAppointmentSuccessReply(SelectedDoctor selectedDoctor, SlotInfoDTO slot, Long appointmentId) {
        return "预约已成功创建！\n\n"
                + "- 医生：" + selectedDoctor.name() + "\n"
                + "- 就诊时间：" + slot.getScheduleDate() + " " + displayPeriod(slot.getPeriod())
                + " " + slot.getStartTime() + "-" + slot.getEndTime() + "\n\n"
                + "请您按时就诊，祝您早日康复！" + DISCLAIMER;
    }

    private List<DoctorCandidate> findAvailableDoctors(String keywords, AppointmentTime appointmentTime) {
        for (String keyword : searchKeywordCandidates(keywords)) {
            R<List<DoctorInfoDTO>> response = remoteDoctorService.searchBySymptom(keyword);
            List<DoctorInfoDTO> doctors = response != null && response.isSuccess() && response.getData() != null
                    ? response.getData()
                    : List.of();

            Map<Long, DoctorCandidate> candidates = new LinkedHashMap<>();
            for (DoctorInfoDTO doctor : doctors) {
                if (doctor == null || doctor.getId() == null) {
                    continue;
                }
                Optional<SlotInfoDTO> slot = getAvailableSlots(doctor.getId(), appointmentTime.date()).stream()
                        .filter(s -> appointmentTime.period().equals(s.getPeriod()))
                        .filter(this::hasCapacity)
                        .findFirst();
                slot.ifPresent(slotInfo -> candidates.putIfAbsent(doctor.getId(), new DoctorCandidate(doctor, slotInfo)));
                if (candidates.size() >= 5) {
                    break;
                }
            }
            if (!candidates.isEmpty()) {
                return new ArrayList<>(candidates.values());
            }
        }
        return List.of();
    }

    private TriageInfo extractTriageInfo(String text, ConversationSummaryVO summary) {
        if (summary != null) {
            return new TriageInfo(
                    hasSummaryValue(summary.getChiefComplaint()),
                    hasSummaryValue(summary.getDuration()),
                    hasSummaryValue(summary.getSymptoms()),
                    hasSummaryValue(summary.getSeverity()),
                    hasSummaryValue(summary.getMedicalHistory()));
        }
        String source = text == null ? "" : text;
        boolean hasSymptom = SYMPTOM_KEYWORDS.stream().anyMatch(source::contains)
                || source.contains("不舒服") || source.contains("疼") || source.contains("痛");
        boolean hasDuration = Pattern.compile("([0-9一二两三四五六七八九十半]+\\s*(天|日|周|星期|月|小时|分钟))|今天|昨天|刚开始|好几天|一周|半天|昨晚")
                .matcher(source)
                .find();
        boolean hasAssociatedInfo = containsAny(source,
                "伴有", "还有", "同时", "发热", "发烧", "胸闷", "气短", "呼吸困难", "呕吐", "恶心", "头晕",
                "没有其他", "无其他", "不伴", "没有发烧", "没有发热");
        boolean hasSeverityInfo = containsAny(source,
                "轻微", "较轻", "中等", "一般", "严重", "剧烈", "很痛", "明显", "影响",
                "不能", "睡眠", "进食", "日常", "活动", "还好", "不严重", "可忍受");
        boolean hasMedicalHistory = containsAny(source,
                "既往史", "基础病", "过敏", "长期用药", "高血压", "糖尿病", "冠心病", "心脏病", "哮喘",
                "没有基础病", "无基础病", "没有过敏史", "无过敏史", "没有既往史", "无既往史");
        return new TriageInfo(hasSymptom, hasDuration, hasAssociatedInfo, hasSeverityInfo, hasMedicalHistory);
    }

    private boolean hasSummaryValue(String value) {
        return value != null && !value.isBlank() && !"未提及".equals(value) && !"-".equals(value);
    }

    private SelectedDoctor resolveSelectedDoctor(FlowContext context) {
        Long doctorId = extractLong(context.latestUserText(), DOCTOR_ID_PATTERN);
        if (doctorId == null) {
            doctorId = resolveDoctorIdFromOption(context);
        }
        if (doctorId == null && isConfirming(context.latestUserText()) && context.lastAssistantText().contains("确认预约")) {
            doctorId = extractLong(context.lastAssistantText(), DOCTOR_ID_PATTERN);
        }
        if (doctorId == null && isConfirming(context.latestUserText())) {
            doctorId = resolveDoctorIdFromConfirmation(context);
        }
        if (doctorId == null) {
            doctorId = resolveDoctorIdByName(context);
        }
        if (doctorId != null) {
            R<DoctorInfoDTO> response = remoteDoctorService.getDoctorById(doctorId);
            if (response != null && response.isSuccess() && response.getData() != null) {
                return new SelectedDoctor(response.getData().getId(), response.getData().getName());
            }
            return new SelectedDoctor(doctorId, "所选医生");
        }
        return null;
    }

    private Long resolveDoctorIdByName(FlowContext context) {
        String latestUserText = context.latestUserText();
        for (DoctorOption option : extractDoctorOptions(context.lastAssistantText())) {
            if (latestUserText.contains(option.name())) {
                return option.doctorId();
            }
        }
        if (!latestUserText.isBlank()) {
            R<DoctorInfoDTO> response = remoteDoctorService.getDoctorByName(latestUserText.trim());
            if (response != null && response.isSuccess() && response.getData() != null) {
                return response.getData().getId();
            }
        }
        return null;
    }

    private Long resolveDoctorIdFromConfirmation(FlowContext context) {
        Matcher matcher = CONFIRMATION_DOCTOR_PATTERN.matcher(context.lastAssistantText());
        if (!matcher.find()) {
            return null;
        }
        R<DoctorInfoDTO> response = remoteDoctorService.getDoctorByName(matcher.group(1).trim());
        if (response != null && response.isSuccess() && response.getData() != null) {
            return response.getData().getId();
        }
        return null;
    }

    private Long resolveDoctorIdFromOption(FlowContext context) {
        Matcher matcher = OPTION_SELECTION_PATTERN.matcher(context.latestUserText());
        if (!matcher.find()) {
            return null;
        }
        int optionIndex = Integer.parseInt(matcher.group(1));
        for (DoctorOption option : extractDoctorOptions(context.lastAssistantText())) {
            if (option.index() == optionIndex) {
                return option.doctorId();
            }
        }
        return null;
    }

    private SlotInfoDTO resolveSelectedSlot(FlowContext context, SelectedDoctor selectedDoctor, AppointmentTime appointmentTime) {
        Long explicitSlotId = extractLong(context.latestUserText(), SLOT_ID_PATTERN);
        if (explicitSlotId == null) {
            explicitSlotId = extractLong(context.lastAssistantText(), SLOT_ID_PATTERN);
        }
        List<SlotInfoDTO> slots = getAvailableSlots(selectedDoctor.doctorId(), appointmentTime.date()).stream()
                .filter(slot -> appointmentTime.period().equals(slot.getPeriod()))
                .filter(this::hasCapacity)
                .toList();
        if (explicitSlotId != null) {
            Long selectedSlotId = explicitSlotId;
            return slots.stream()
                    .filter(slot -> Objects.equals(slot.getId(), selectedSlotId))
                    .findFirst()
                    .orElse(null);
        }
        return slots.size() == 1 ? slots.get(0) : null;
    }

    private Long createOrReuseAppointment(Long patientId, Long doctorId, Long slotId, Long sessionId) {
        R<AppointmentDTO> existing = remoteAppointmentService.getAppointmentByPatientAndSlot(patientId, slotId);
        if (existing != null && existing.isSuccess() && existing.getData() != null) {
            AppointmentDTO appointment = existing.getData();
            if (appointment.getSessionId() == null && appointment.getId() != null) {
                remoteAppointmentService.bindSession(appointment.getId(), sessionId);
            }
            return appointment.getId();
        }
        R<Long> created = remoteAppointmentService.createAppointment(patientId, doctorId, slotId, sessionId);
        if (created == null || !created.isSuccess() || created.getData() == null) {
            log.warn("Failed to create appointment in triage flow, patientId={}, doctorId={}, slotId={}, sessionId={}, responseCode={}, responseMsg={}",
                    patientId,
                    doctorId,
                    slotId,
                    sessionId,
                    created == null ? null : created.getCode(),
                    created == null ? null : created.getMsg());
            return null;
        }
        return created.getData();
    }

    private List<SlotInfoDTO> getAvailableSlots(Long doctorId, LocalDate date) {
        R<List<SlotInfoDTO>> response = remoteScheduleService.getAvailableSlots(doctorId, date.toString());
        return response != null && response.isSuccess() && response.getData() != null
                ? response.getData()
                : List.of();
    }

    private AppointmentTime extractAppointmentTime(String text) {
        LocalDate date = extractDate(text);
        String period = extractPeriod(text);
        if (date == null || period == null) {
            return null;
        }
        return new AppointmentTime(date, period);
    }

    private LocalDate extractDate(String text) {
        if (text == null) {
            return null;
        }
        Matcher cn = DATE_CN_PATTERN.matcher(text);
        if (cn.find()) {
            return LocalDate.of(Integer.parseInt(cn.group(1)), Integer.parseInt(cn.group(2)), Integer.parseInt(cn.group(3)));
        }
        Matcher dash = DATE_DASH_PATTERN.matcher(text);
        if (dash.find()) {
            return LocalDate.of(Integer.parseInt(dash.group(1)), Integer.parseInt(dash.group(2)), Integer.parseInt(dash.group(3)));
        }
        LocalDate today = LocalDate.now(SERVER_ZONE);
        if (text.contains("后天")) {
            return today.plusDays(2);
        }
        if (text.contains("明天")) {
            return today.plusDays(1);
        }
        if (text.contains("今天")) {
            return today;
        }
        return null;
    }

    private String extractPeriod(String text) {
        if (text == null) {
            return null;
        }
        if (text.contains("下午") || text.contains("14:") || text.contains("18:")) {
            return "afternoon";
        }
        if (text.contains("上午") || text.contains("早上") || text.contains("08:") || text.contains("8:") || text.contains("12:")) {
            return "morning";
        }
        return null;
    }

    private List<DoctorOption> extractDoctorOptions(String text) {
        List<DoctorOption> options = new ArrayList<>();
        String source = text == null ? "" : text;
        Matcher matcher = DOCTOR_OPTION_PATTERN.matcher(source);
        while (matcher.find()) {
            options.add(new DoctorOption(
                    Integer.parseInt(matcher.group(1)),
                    matcher.group(2).trim(),
                    Long.parseLong(matcher.group(3))));
        }
        if (!options.isEmpty()) {
            return options;
        }
        Matcher displayMatcher = DOCTOR_OPTION_DISPLAY_PATTERN.matcher(source);
        while (displayMatcher.find()) {
            String name = displayMatcher.group(2).trim();
            R<DoctorInfoDTO> response = remoteDoctorService.getDoctorByName(name);
            if (response != null && response.isSuccess() && response.getData() != null) {
                options.add(new DoctorOption(
                        Integer.parseInt(displayMatcher.group(1)),
                        name,
                        response.getData().getId()));
            }
        }
        return options;
    }

    private boolean isConfirming(String text) {
        return text != null && (text.contains("确认") || text.contains("预约") || text.contains("创建") || text.contains("提交"));
    }

    private boolean hasCapacity(SlotInfoDTO slot) {
        return slot != null && slot.getId() != null && slot.getAvailableSlots() != null && slot.getAvailableSlots() > 0;
    }

    private Long extractLong(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    private List<String> searchKeywordCandidates(String text) {
        List<String> candidates = new ArrayList<>();
        String source = text == null ? "" : text;
        for (String keyword : SYMPTOM_KEYWORDS) {
            if (source.contains(keyword) && !candidates.contains(keyword)) {
                candidates.add(keyword);
            }
        }

        String cleaned = source
                .replaceAll("\\d{4}年\\d{1,2}月\\d{1,2}日?", " ")
                .replaceAll("\\d{4}-\\d{1,2}-\\d{1,2}", " ")
                .replaceAll("今天|明天|后天|上午|下午|早上|中午|晚上", " ")
                .replaceAll("预约|挂号|医生|就诊|想|我要|我想|帮我|找|看看|看病|时间|日期|时段", " ")
                .replaceAll("[，。！？、,.!?：:；;（）()\\s]+", " ")
                .trim();
        if (!cleaned.isBlank() && !candidates.contains(cleaned)) {
            candidates.add(cleaned);
        }

        if (candidates.isEmpty()) {
            candidates.add("常见症状");
        }
        return candidates;
    }

    private String displayPeriod(String period) {
        return "afternoon".equals(period) ? "下午" : "上午";
    }

    private String nullToDash(Object value) {
        return value == null || String.valueOf(value).isBlank() ? "-" : String.valueOf(value);
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

    public record TriageFlowResult(String reply, Long appointmentId, List<String> suggestedReplies) {
        public TriageFlowResult(String reply, Long appointmentId) {
            this(reply, appointmentId, List.of());
        }
    }

    private record TriageInfo(boolean hasSymptom,
                              boolean hasDuration,
                              boolean hasAssociatedInfo,
                              boolean hasSeverityInfo,
                              boolean hasMedicalHistory) {
    }

    private record AppointmentTime(LocalDate date, String period) {
        String display() {
            return date + " " + ("afternoon".equals(period) ? "下午" : "上午");
        }
    }

    private record SelectedDoctor(Long doctorId, String name) {
    }

    private record DoctorCandidate(DoctorInfoDTO doctor, SlotInfoDTO slot) {
    }

    private record DoctorOption(int index, String name, Long doctorId) {
    }

    private record FlowContext(List<ChatMessage> messages) {
        static FlowContext from(List<ChatMessage> messages) {
            return new FlowContext(messages == null ? List.of() : messages);
        }

        String latestUserText() {
            for (int i = messages.size() - 1; i >= 0; i--) {
                ChatMessage message = messages.get(i);
                if ("user".equals(message.getRole())) {
                    return message.getContent() == null ? "" : message.getContent();
                }
            }
            return "";
        }

        String lastAssistantText() {
            for (int i = messages.size() - 1; i >= 0; i--) {
                ChatMessage message = messages.get(i);
                if ("assistant".equals(message.getRole())) {
                    return message.getContent() == null ? "" : message.getContent();
                }
            }
            return "";
        }

        String allUserText() {
            return messages.stream()
                    .filter(message -> "user".equals(message.getRole()))
                    .map(ChatMessage::getContent)
                    .filter(Objects::nonNull)
                    .reduce("", (left, right) -> left + "\n" + right);
        }

        String allAssistantText() {
            return messages.stream()
                    .filter(message -> "assistant".equals(message.getRole()))
                    .map(ChatMessage::getContent)
                    .filter(Objects::nonNull)
                    .reduce("", (left, right) -> left + "\n" + right);
        }

        String allText() {
            return messages.stream()
                    .map(ChatMessage::getContent)
                    .filter(Objects::nonNull)
                    .reduce("", (left, right) -> left + "\n" + right);
        }
    }
}
