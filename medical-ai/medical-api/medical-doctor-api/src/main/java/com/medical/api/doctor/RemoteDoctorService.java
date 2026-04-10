package com.medical.api.doctor;

import com.medical.api.doctor.dto.DoctorInfoDTO;
import com.medical.common.core.domain.R;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "medical-doctor-service", path = "/doctor")
public interface RemoteDoctorService {
    @GetMapping("/inner/{doctorId}")
    R<DoctorInfoDTO> getDoctorById(@PathVariable("doctorId") Long doctorId);

    @GetMapping("/inner/by-user/{userId}")
    R<DoctorInfoDTO> getDoctorByUserId(@PathVariable("userId") Long userId);

    @GetMapping("/inner/search")
    R<List<DoctorInfoDTO>> searchBySymptom(@RequestParam("keywords") String keywords);
}
