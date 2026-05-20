package com.medical.ai.service.impl;

import com.medical.ai.domain.entity.ChatMessage;
import com.medical.api.appointment.RemoteAppointmentService;
import com.medical.api.appointment.dto.AppointmentDTO;
import com.medical.api.doctor.RemoteDoctorService;
import com.medical.api.doctor.RemoteScheduleService;
import com.medical.api.doctor.dto.DoctorInfoDTO;
import com.medical.api.doctor.dto.SlotInfoDTO;
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
    private static final List<String> SYMPTOM_KEYWORDS = List.of(
            "咳嗽", "发热", "发烧", "感冒", "嗓子痛", "咽痛", "头痛", "头疼", "头晕", "眩晕",
            "胸痛", "胸闷", "腹痛", "胃痛", "皮疹", "失眠", "心悸", "鼻塞", "流涕", "恶心");
    private static final String DISCLAIMER = "\n\nAI导诊仅供参考，不能替代专业医生诊断。";

    private final RemoteDoctorService remoteDoctorService;
    private final RemoteScheduleService remoteScheduleService;
    private final RemoteAppointmentService remoteAppointmentService;

    public TriageFlowResult handle(Long sessionId, Long patientId, List<ChatMessage> messages) {
        FlowContext context = FlowContext.from(messages);
        AppointmentTime appointmentTime = extractAppointmentTime(context.allUserText());
        if (appointmentTime == null) {
            return new TriageFlowResult("""
                    为了先把预约范围定下来，请您先告诉我想预约的日期和时段。

                    例如：2026年5月25日上午，或 2026-05-25 下午。""".strip() + DISCLAIMER, null);
        }

        SelectedDoctor selectedDoctor = resolveSelectedDoctor(context);
        if (selectedDoctor == null) {
            return new TriageFlowResult(buildDoctorSelectionReply(context, appointmentTime), null);
        }

        SlotInfoDTO selectedSlot = resolveSelectedSlot(context, selectedDoctor, appointmentTime);
        if (selectedSlot == null) {
            return new TriageFlowResult(buildSlotSelectionReply(selectedDoctor, appointmentTime), null);
        }

        if (!isConfirming(context.latestUserText())) {
            return new TriageFlowResult(buildAppointmentConfirmationReply(selectedDoctor, selectedSlot), null);
        }

        Long appointmentId = createOrReuseAppointment(patientId, selectedDoctor.doctorId(), selectedSlot.getId(), sessionId);
        return new TriageFlowResult(buildAppointmentSuccessReply(selectedDoctor, selectedSlot, appointmentId), appointmentId);
    }

    private String buildDoctorSelectionReply(FlowContext context, AppointmentTime appointmentTime) {
        List<DoctorCandidate> candidates = findAvailableDoctors(context.allUserText(), appointmentTime);
        if (candidates.isEmpty()) {
            return "您想预约的时间是：" + appointmentTime.display() + "。\n\n"
                    + "我暂时没有找到该时段有可用号源的匹配医生。请换一个日期或时段，我再帮您查。"
                    + DISCLAIMER;
        }

        StringBuilder reply = new StringBuilder();
        reply.append("您想预约的时间是：").append(appointmentTime.display()).append("。\n\n");
        reply.append("我按您的症状和该时间段查到了这些可预约医生，请回复序号或医生姓名选择：\n");
        for (int i = 0; i < candidates.size(); i++) {
            DoctorCandidate candidate = candidates.get(i);
            reply.append(i + 1)
                    .append(". ")
                    .append(candidate.doctor().getName())
                    .append("（doctorId=")
                    .append(candidate.doctor().getId())
                    .append("，")
                    .append(nullToDash(candidate.doctor().getDepartmentNames()))
                    .append("，")
                    .append(nullToDash(candidate.doctor().getTitle()))
                    .append("，挂号费=")
                    .append(candidate.doctor().getConsultationFee() == null ? "系统当前显示挂号费为0元" : candidate.doctor().getConsultationFee() + "元")
                    .append("，slotId=")
                    .append(candidate.slot().getId())
                    .append("）\n");
        }
        reply.append("\n请回复“选1”或医生姓名。").append(DISCLAIMER);
        return reply.toString();
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
                + "- 医生：" + selectedDoctor.name() + "（doctorId=" + selectedDoctor.doctorId() + "）\n"
                + "- 就诊时间：" + slot.getScheduleDate() + " " + displayPeriod(slot.getPeriod())
                + " " + slot.getStartTime() + "-" + slot.getEndTime() + "\n"
                + "- 号源：slotId=" + slot.getId() + "，剩余 " + slot.getAvailableSlots() + " 个\n\n"
                + "请回复“确认预约”，我就为您创建预约。" + DISCLAIMER;
    }

    private String buildAppointmentSuccessReply(SelectedDoctor selectedDoctor, SlotInfoDTO slot, Long appointmentId) {
        return "预约已成功创建！\n\n"
                + "- 医生：" + selectedDoctor.name() + "（doctorId=" + selectedDoctor.doctorId() + "）\n"
                + "- 就诊时间：" + slot.getScheduleDate() + " " + displayPeriod(slot.getPeriod())
                + " " + slot.getStartTime() + "-" + slot.getEndTime() + "\n"
                + "- 预约编号：" + appointmentId + "\n\n"
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

    private SelectedDoctor resolveSelectedDoctor(FlowContext context) {
        Long doctorId = extractLong(context.latestUserText(), DOCTOR_ID_PATTERN);
        if (doctorId == null) {
            doctorId = resolveDoctorIdFromOption(context);
        }
        if (doctorId == null) {
            doctorId = resolveDoctorIdByName(context);
        }
        if (doctorId == null && isConfirming(context.latestUserText()) && context.lastAssistantText().contains("确认预约")) {
            doctorId = extractLong(context.lastAssistantText(), DOCTOR_ID_PATTERN);
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
            throw new IllegalStateException("预约创建失败，请稍后重试");
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
        Matcher matcher = DOCTOR_OPTION_PATTERN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            options.add(new DoctorOption(
                    Integer.parseInt(matcher.group(1)),
                    matcher.group(2).trim(),
                    Long.parseLong(matcher.group(3))));
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

    public record TriageFlowResult(String reply, Long appointmentId) {
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

        String allText() {
            return messages.stream()
                    .map(ChatMessage::getContent)
                    .filter(Objects::nonNull)
                    .reduce("", (left, right) -> left + "\n" + right);
        }
    }
}
