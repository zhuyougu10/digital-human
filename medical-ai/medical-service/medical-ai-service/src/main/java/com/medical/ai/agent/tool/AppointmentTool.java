package com.medical.ai.agent.tool;

import com.medical.api.appointment.RemoteAppointmentService;
import com.medical.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentTool {

    private final RemoteAppointmentService remoteAppointmentService;

    @Bean
    @Description("为患者创建预约挂号。输入patientId、doctorId、slotId。返回预约ID。")
    public Function<CreateAppointmentRequest, Map<String, Object>> createAppointment() {
        return request -> {
            log.info("Function call: createAppointment, patientId={}, doctorId={}, slotId={}",
                    request.getPatientId(), request.getDoctorId(), request.getSlotId());
            R<Long> result = remoteAppointmentService.createAppointment(
                    request.getPatientId(), request.getDoctorId(), request.getSlotId());
            if (result.isSuccess()) {
                return Map.of("success", true, "appointmentId", result.getData());
            }
            return Map.of("success", false, "message", "预约失败，请稍后重试");
        };
    }
}
