package com.medical.ai.agent.tool;

import com.medical.api.doctor.RemoteDoctorService;
import com.medical.api.doctor.RemoteScheduleService;
import com.medical.api.doctor.dto.DoctorInfoDTO;
import com.medical.api.doctor.dto.SlotInfoDTO;
import com.medical.common.core.domain.R;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

@Slf4j
@Component
public class DoctorSearchTool {

    private final RemoteDoctorService remoteDoctorService;
    private final RemoteScheduleService remoteScheduleService;
    private final Executor toolCallExecutor;

    public DoctorSearchTool(RemoteDoctorService remoteDoctorService,
                            RemoteScheduleService remoteScheduleService,
                            @Qualifier("toolCallExecutor") Executor toolCallExecutor) {
        this.remoteDoctorService = remoteDoctorService;
        this.remoteScheduleService = remoteScheduleService;
        this.toolCallExecutor = toolCallExecutor;
    }

    @Bean
    @Description("根据患者症状关键词搜索推荐的科室和医生。输入keywords为逗号分隔的症状关键词。")
    public Function<SearchDoctorRequest, List<DoctorInfoDTO>> searchDoctorBySymptom() {
        return request -> {
            log.info("Function call: searchDoctorBySymptom, keywords={}", request.getKeywords());
            return CompletableFuture.supplyAsync(() -> {
                R<List<DoctorInfoDTO>> result = remoteDoctorService.searchBySymptom(request.getKeywords());
                return result.isSuccess() ? result.getData() : List.<DoctorInfoDTO>of();
            }, toolCallExecutor).join();
        };
    }

    @Bean
    @Description("查询指定医生在指定日期的可预约时间段。输入doctorId、doctorName和date(yyyy-MM-dd)。doctorName必须与doctorId对应，用于校验返回结果是否为同一位医生。")
    public Function<GetSlotsRequest, List<SlotInfoDTO>> getAvailableSlots() {
        return request -> {
            log.info("Function call: getAvailableSlots, doctorId={}, doctorName={}, date={}",
                    request.getDoctorId(), request.getDoctorName(), request.getDate());
            return CompletableFuture.supplyAsync(() -> {
                validateRequest(request);

                DoctorSelection selection = resolveDoctorSelection(request);
                R<List<SlotInfoDTO>> result = remoteScheduleService.getAvailableSlots(
                        selection.doctorId(), request.getDate());
                List<SlotInfoDTO> slots = result != null && result.isSuccess() && result.getData() != null
                        ? result.getData()
                        : Collections.emptyList();
                validateReturnedSlots(selection.doctorId(), selection.doctorName(), slots);
                return slots;
            }, toolCallExecutor).join();
        };
    }

    private void validateRequest(GetSlotsRequest request) {
        if (request == null || request.getDoctorId() == null || request.getDate() == null || request.getDate().isBlank()) {
            throw new IllegalArgumentException("doctorId 和 date 不能为空");
        }
    }

    private DoctorInfoDTO loadDoctor(Long doctorId) {
        R<DoctorInfoDTO> doctorResult = remoteDoctorService.getDoctorById(doctorId);
        if (doctorResult == null || !doctorResult.isSuccess() || doctorResult.getData() == null) {
            throw new IllegalStateException("未找到对应医生信息，请重新选择医生");
        }
        return doctorResult.getData();
    }

    private DoctorSelection resolveDoctorSelection(GetSlotsRequest request) {
        DoctorInfoDTO doctorById = loadDoctor(request.getDoctorId());
        String requestedName = normalize(request.getDoctorName());
        if (requestedName.isBlank() || requestedName.equals(normalize(doctorById.getName()))) {
            return new DoctorSelection(doctorById.getId(), doctorById.getName());
        }

        R<DoctorInfoDTO> byNameResult = remoteDoctorService.getDoctorByName(request.getDoctorName().trim());
        if (byNameResult == null || !byNameResult.isSuccess() || byNameResult.getData() == null) {
            throw new IllegalArgumentException("医生姓名与 doctorId 不匹配，且无法按姓名定位医生，请重新确认后再查询号源");
        }

        DoctorInfoDTO doctorByName = byNameResult.getData();
        log.warn("Doctor selection mismatch corrected: requestedDoctorId={}, requestedDoctorName={}, resolvedDoctorId={}, resolvedDoctorName={}",
                request.getDoctorId(), request.getDoctorName(), doctorByName.getId(), doctorByName.getName());
        return new DoctorSelection(doctorByName.getId(), doctorByName.getName());
    }

    private void validateReturnedSlots(Long doctorId, String doctorName, List<SlotInfoDTO> slots) {
        for (SlotInfoDTO slot : slots) {
            if (slot == null) {
                continue;
            }
            if (!doctorId.equals(slot.getDoctorId())
                    || !normalize(doctorName).equals(normalize(slot.getDoctorName()))) {
                log.error("Slot doctor mismatch, requestDoctorId={}, requestDoctorName={}, actualDoctorId={}, actualDoctorName={}, slotId={}",
                        doctorId, doctorName, slot.getDoctorId(), slot.getDoctorName(), slot.getId());
                throw new IllegalStateException("号源返回的医生信息与所选医生不一致，请重新查询");
            }
        }
    }

    private record DoctorSelection(Long doctorId, String doctorName) {
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }
}
