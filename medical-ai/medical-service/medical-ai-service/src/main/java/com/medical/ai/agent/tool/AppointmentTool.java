package com.medical.ai.agent.tool;

import com.medical.api.appointment.RemoteAppointmentService;
import com.medical.common.core.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

@Slf4j
@Component
public class AppointmentTool {

    private final RemoteAppointmentService remoteAppointmentService;
    private final Executor toolCallExecutor;

    public AppointmentTool(RemoteAppointmentService remoteAppointmentService,
                           @Qualifier("toolCallExecutor") Executor toolCallExecutor) {
        this.remoteAppointmentService = remoteAppointmentService;
        this.toolCallExecutor = toolCallExecutor;
    }

    @Bean
    @Description("为患者创建预约挂号。输入patientId、doctorId、slotId。返回预约ID。")
    public Function<CreateAppointmentRequest, Map<String, Object>> createAppointment() {
        return request -> {
            log.info("Function call: createAppointment, patientId={}, doctorId={}, slotId={}",
                    request.getPatientId(), request.getDoctorId(), request.getSlotId());
            return CompletableFuture.supplyAsync(() -> {
                R<Long> result = remoteAppointmentService.createAppointment(
                        request.getPatientId(), request.getDoctorId(), request.getSlotId());
                if (result.isSuccess()) {
                    return Map.<String, Object>of("success", true, "appointmentId", result.getData());
                }
                return Map.<String, Object>of("success", false, "message", "预约失败，请稍后重试");
            }, toolCallExecutor).join();
        };
    }
}
