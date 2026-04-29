package com.medical.api.appointment;

import com.medical.api.appointment.dto.AppointmentDTO;
import com.medical.common.core.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "medical-appointment-service", path = "/appointment")
public interface RemoteAppointmentService {
    @GetMapping("/inner/{appointmentId}")
    R<AppointmentDTO> getAppointmentSnapshot(@PathVariable("appointmentId") Long appointmentId);

    @PostMapping("/inner/create")
    R<Long> createAppointment(@RequestParam("patientId") Long patientId,
                              @RequestParam("doctorId") Long doctorId,
                              @RequestParam("slotId") Long slotId);
}
