package com.medical.doctor.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.medical.api.doctor.dto.SlotInfoDTO;
import com.medical.common.core.constant.UserConstants;
import com.medical.common.core.domain.R;
import com.medical.doctor.domain.dto.ScheduleTemplateDTO;
import com.medical.doctor.domain.entity.ScheduleTemplate;
import com.medical.doctor.domain.vo.ScheduleSlotVO;
import com.medical.doctor.service.ScheduleService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping("/template/{doctorId}")
    public R<List<ScheduleTemplate>> templates(@PathVariable Long doctorId) {
        return R.ok(scheduleService.getTemplatesByDoctor(doctorId));
    }

    @PostMapping("/template/{doctorId}")
    @SaCheckRole(value = {UserConstants.ROLE_ADMIN, UserConstants.ROLE_DOCTOR}, mode = SaMode.OR)
    public R<Void> saveTemplate(@PathVariable Long doctorId, @RequestBody @Valid ScheduleTemplateDTO dto) {
        scheduleService.saveTemplate(doctorId, dto);
        return R.ok();
    }

    @DeleteMapping("/template/{templateId}")
    @SaCheckRole(value = {UserConstants.ROLE_ADMIN, UserConstants.ROLE_DOCTOR}, mode = SaMode.OR)
    public R<Void> deleteTemplate(@PathVariable Long templateId) {
        scheduleService.deleteTemplate(templateId);
        return R.ok();
    }

    @GetMapping("/slots")
    public R<List<ScheduleSlotVO>> getAvailableSlots(
            @RequestParam("doctorId") Long doctorId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return R.ok(scheduleService.getAvailableSlots(doctorId, date));
    }

    @GetMapping("/slots/department")
    public R<List<ScheduleSlotVO>> getAvailableSlotsByDepartment(
            @RequestParam("departmentId") Long departmentId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return R.ok(scheduleService.getAvailableSlotsByDepartment(departmentId, date));
    }

    @SaCheckRole(UserConstants.ROLE_ADMIN)
    @PostMapping("/generate")
    public R<Void> generate(
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        scheduleService.generateSlots(startDate, endDate);
        return R.ok();
    }

    @GetMapping("/inner/slots")
    public R<List<SlotInfoDTO>> innerGetAvailableSlots(
            @RequestParam("doctorId") Long doctorId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<SlotInfoDTO> result = scheduleService.getAvailableSlots(doctorId, date)
                .stream()
                .map(this::toSlotInfoDTO)
                .collect(Collectors.toList());
        return R.ok(result);
    }

    @PostMapping("/inner/slots/{slotId}/book")
    public R<Boolean> innerBookSlot(@PathVariable Long slotId) {
        return R.ok(scheduleService.bookSlot(slotId));
    }

    @PostMapping("/inner/slots/{slotId}/cancel")
    public R<Boolean> innerCancelSlot(@PathVariable Long slotId) {
        return R.ok(scheduleService.cancelSlot(slotId));
    }

    private SlotInfoDTO toSlotInfoDTO(ScheduleSlotVO slotVO) {
        SlotInfoDTO dto = new SlotInfoDTO();
        dto.setId(slotVO.getId());
        dto.setDoctorId(slotVO.getDoctorId());
        dto.setDoctorName(slotVO.getDoctorName());
        dto.setScheduleDate(slotVO.getScheduleDate());
        dto.setPeriod(slotVO.getPeriod());
        dto.setStartTime(slotVO.getStartTime());
        dto.setEndTime(slotVO.getEndTime());
        dto.setTotalSlots(slotVO.getTotalSlots());
        dto.setBookedSlots(slotVO.getBookedSlots());
        dto.setAvailableSlots(slotVO.getAvailableSlots());
        return dto;
    }
}
