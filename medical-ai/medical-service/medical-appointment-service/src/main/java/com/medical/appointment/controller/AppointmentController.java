package com.medical.appointment.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.medical.appointment.domain.dto.AppointmentQueryDTO;
import com.medical.appointment.domain.dto.CreateAppointmentDTO;
import com.medical.appointment.domain.vo.AppointmentListVO;
import com.medical.appointment.domain.vo.AppointmentVO;
import com.medical.appointment.service.AppointmentService;
import com.medical.common.core.constant.UserConstants;
import com.medical.common.core.domain.PageQuery;
import com.medical.common.core.domain.PageResult;
import com.medical.common.core.domain.R;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/appointment")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @SaCheckLogin
    @PostMapping
    public R<Long> create(@RequestBody @Valid CreateAppointmentDTO dto) {
        dto.setPatientId(StpUtil.getLoginIdAsLong());
        return R.ok(appointmentService.createAppointment(dto));
    }

    @SaCheckLogin
    @GetMapping("/my")
    public R<PageResult<AppointmentListVO>> myAppointments(PageQuery pageQuery) {
        return R.ok(appointmentService.getMyAppointments(StpUtil.getLoginIdAsLong(), pageQuery));
    }

    @SaCheckRole(UserConstants.ROLE_DOCTOR)
    @GetMapping("/doctor")
    public R<List<AppointmentVO>> doctorAppointments(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return R.ok(appointmentService.getDoctorAppointments(StpUtil.getLoginIdAsLong(), date));
    }

    @SaCheckLogin
    @GetMapping("/{id}")
    public R<AppointmentVO> detail(@PathVariable("id") Long id) {
        return R.ok(appointmentService.getAppointmentDetail(id, StpUtil.getLoginIdAsLong()));
    }

    @SaCheckLogin
    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable("id") Long id) {
        appointmentService.cancelAppointment(id, StpUtil.getLoginIdAsLong());
        return R.ok();
    }

    @SaCheckRole(UserConstants.ROLE_ADMIN)
    @GetMapping("/list")
    public R<PageResult<AppointmentListVO>> list(AppointmentQueryDTO queryDTO, PageQuery pageQuery) {
        return R.ok(appointmentService.listAll(queryDTO, pageQuery));
    }

    @SaCheckRole(UserConstants.ROLE_ADMIN)
    @GetMapping("/statistics")
    public R<Map<String, Object>> statistics(
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(appointmentService.getStatistics(startDate, endDate));
    }

    @PostMapping("/inner/create")
    public R<Long> innerCreate(@RequestParam("patientId") Long patientId,
                               @RequestParam("doctorId") Long doctorId,
                               @RequestParam("slotId") Long slotId,
                               @RequestParam(value = "departmentId", required = false) Long departmentId,
                               @RequestParam(value = "sessionId", required = false) Long sessionId) {
        CreateAppointmentDTO dto = new CreateAppointmentDTO();
        dto.setPatientId(patientId);
        dto.setDoctorId(doctorId);
        dto.setSlotId(slotId);
        dto.setDepartmentId(departmentId == null ? 0L : departmentId);
        dto.setSessionId(sessionId);
        return R.ok(appointmentService.createAppointment(dto));
    }
}
