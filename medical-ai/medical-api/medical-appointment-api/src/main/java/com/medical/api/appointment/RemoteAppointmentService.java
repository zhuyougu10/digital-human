package com.medical.api.appointment;

import com.medical.common.core.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "medical-appointment-service", path = "/appointment")
public interface RemoteAppointmentService {
    @PostMapping("/inner/create")
    R<Long> createAppointment(@RequestParam("patientId") Long patientId,
                              @RequestParam("doctorId") Long doctorId,
                              @RequestParam("slotId") Long slotId);
}
