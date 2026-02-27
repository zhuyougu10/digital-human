package com.medical.ai.agent.tool;

import com.medical.api.doctor.RemoteDoctorService;
import com.medical.api.doctor.RemoteScheduleService;
import com.medical.api.doctor.dto.DoctorInfoDTO;
import com.medical.api.doctor.dto.SlotInfoDTO;
import com.medical.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class DoctorSearchTool {

    private final RemoteDoctorService remoteDoctorService;
    private final RemoteScheduleService remoteScheduleService;

    @Bean
    @Description("根据患者症状关键词搜索推荐的科室和医生。输入keywords为逗号分隔的症状关键词。")
    public Function<SearchDoctorRequest, List<DoctorInfoDTO>> searchDoctorBySymptom() {
        return request -> {
            log.info("Function call: searchDoctorBySymptom, keywords={}", request.getKeywords());
            R<List<DoctorInfoDTO>> result = remoteDoctorService.searchBySymptom(request.getKeywords());
            return result.isSuccess() ? result.getData() : List.of();
        };
    }

    @Bean
    @Description("查询指定医生在指定日期的可预约时间段。输入doctorId和date(yyyy-MM-dd)。")
    public Function<GetSlotsRequest, List<SlotInfoDTO>> getAvailableSlots() {
        return request -> {
            log.info("Function call: getAvailableSlots, doctorId={}, date={}", request.getDoctorId(), request.getDate());
            R<List<SlotInfoDTO>> result = remoteScheduleService.getAvailableSlots(
                    request.getDoctorId(), request.getDate());
            return result.isSuccess() ? result.getData() : List.of();
        };
    }
}
