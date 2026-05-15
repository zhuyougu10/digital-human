package com.medical.ai.agent;

import com.medical.api.doctor.RemoteDoctorService;
import com.medical.common.core.domain.R;
import java.time.Duration;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class TriageAgent implements Agent {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss");
    private static final ZoneId SERVER_ZONE = ZoneId.systemDefault();
    private static final Duration DEPARTMENT_CACHE_TTL = Duration.ofMinutes(5);
    private static final List<String> FALLBACK_DEPARTMENTS = List.of(
            "内科", "外科", "神经内科", "儿科", "妇产科", "眼科", "耳鼻喉科", "皮肤科", "中医科", "口腔科"
    );
    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是一位专业的AI医疗分诊助手。你的职责是：
            1. 通过多轮对话收集患者的症状信息（主诉、伴随症状、持续时间、严重程度）
            2. 在收集到足够信息后，调用 searchDoctorBySymptom 工具为患者推荐合适的科室和医生
            3. 当患者选择医生后，调用 getAvailableSlots 查询可用号源
            4. 当患者确认时间后，调用 createAppointment 完成预约

            当前服务器时间：%s（时区：%s）

            规则：
            - 不要在第一轮就推荐科室，要通过多轮对话逐步收集充分信息
            - 每次回复最多只问一个问题；如果需要继续追问，也要等患者回答后下一轮再问
            - 使用通俗易懂的语言，不要过度使用医学术语
            - 如果症状紧急（如胸痛、呼吸困难、大量出血），立即建议拨打120急救电话
            - 回答涉及今天、明天、本周等相对日期时，必须以上面的服务器时间为准，不得编造日期或时间
            - 推荐科室时，只能从以下系统现有科室中选择：%s
            - 严禁推荐系统中不存在的科室名称，例如呼吸内科、发热门诊、全科医学科
            - 推荐医生时，必须明确展示医生姓名、doctorId、科室、挂号费，并严格使用工具返回的原始信息
            - 当患者指定某位医生后，调用 getAvailableSlots 前必须核对医生姓名和 doctorId，工具参数必须同时传 doctorId、doctorName、date
            - getAvailableSlots 返回后，必须确认返回结果中的 doctorId、doctorName 与用户选择一致，不一致时要明确说明系统数据异常并重新查询，不能继续预约
            - createAppointment 时只能使用 getAvailableSlots 返回的真实 slotId，绝不能使用列表序号或自行猜测的编号
            - 推荐阶段和预约确认阶段的挂号费表述必须一致；若挂号费为 0 元，明确说明“系统当前显示挂号费为 0 元”即可，不要改写成其他金额
            - 每次回复末尾声明：AI导诊仅供参考，不能替代专业医生诊断
            - 保持温和关切的语气
            """;

    private final RemoteDoctorService remoteDoctorService;

    private volatile List<String> cachedDepartmentNames = FALLBACK_DEPARTMENTS;
    private volatile long cachedDepartmentNamesExpireAt;

    @Override
    public String getSystemPrompt() {
        LocalDateTime now = LocalDateTime.now(SERVER_ZONE);
        List<String> departmentNames = getDepartmentNames();
        return SYSTEM_PROMPT_TEMPLATE.formatted(
                now.format(DATE_TIME_FORMATTER),
                SERVER_ZONE,
                String.join("、", departmentNames)
        );
    }

    @Override
    public List<String> getToolNames() {
        return List.of("searchDoctorBySymptom", "getAvailableSlots", "createAppointment");
    }

    @Override
    public String getAgentType() {
        return "TRIAGE";
    }

    private List<String> getDepartmentNames() {
        long now = System.currentTimeMillis();
        if (now < cachedDepartmentNamesExpireAt && !cachedDepartmentNames.isEmpty()) {
            return cachedDepartmentNames;
        }
        return refreshDepartmentNames(now);
    }

    private synchronized List<String> refreshDepartmentNames(long now) {
        if (now < cachedDepartmentNamesExpireAt && !cachedDepartmentNames.isEmpty()) {
            return cachedDepartmentNames;
        }
        try {
            R<List<String>> response = remoteDoctorService.getDepartmentNames();
            if (response != null && response.isSuccess() && response.getData() != null) {
                List<String> departmentNames = response.getData().stream()
                        .filter(name -> name != null && !name.isBlank())
                        .distinct()
                        .toList();
                if (!departmentNames.isEmpty()) {
                    cachedDepartmentNames = departmentNames;
                    cachedDepartmentNamesExpireAt = now + DEPARTMENT_CACHE_TTL.toMillis();
                    return departmentNames;
                }
            }
            log.warn("Failed to load live department names for triage prompt, using fallback. response={}", response);
        } catch (Exception e) {
            log.error("Failed to load live department names for triage prompt, using fallback", e);
        }
        cachedDepartmentNames = FALLBACK_DEPARTMENTS;
        cachedDepartmentNamesExpireAt = now + DEPARTMENT_CACHE_TTL.toMillis();
        return cachedDepartmentNames;
    }
}
