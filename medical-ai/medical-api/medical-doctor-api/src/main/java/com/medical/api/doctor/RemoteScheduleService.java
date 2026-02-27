package com.medical.api.doctor;

import com.medical.api.doctor.dto.SlotInfoDTO;
import com.medical.common.core.domain.R;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "medical-doctor-service", path = "/schedule")
public interface RemoteScheduleService {
    @GetMapping("/inner/slots")
    R<List<SlotInfoDTO>> getAvailableSlots(@RequestParam("doctorId") Long doctorId,
                                           @RequestParam("date") String date);

    @PostMapping("/inner/slots/{slotId}/book")
    R<Boolean> bookSlot(@PathVariable("slotId") Long slotId);

    @PostMapping("/inner/slots/{slotId}/cancel")
    R<Boolean> cancelSlot(@PathVariable("slotId") Long slotId);
}
