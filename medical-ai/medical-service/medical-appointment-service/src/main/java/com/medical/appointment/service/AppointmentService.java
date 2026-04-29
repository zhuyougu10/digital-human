package com.medical.appointment.service;

import com.medical.api.appointment.dto.AppointmentDTO;
import com.medical.appointment.domain.dto.AppointmentQueryDTO;
import com.medical.appointment.domain.dto.CreateAppointmentDTO;
import com.medical.appointment.domain.vo.AppointmentListVO;
import com.medical.appointment.domain.vo.AppointmentVO;
import com.medical.common.core.domain.PageQuery;
import com.medical.common.core.domain.PageResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface AppointmentService {

    Long createAppointment(CreateAppointmentDTO dto);

    void cancelAppointment(Long appointmentId, Long userId);

    PageResult<AppointmentListVO> getMyAppointments(Long userId, PageQuery pageQuery);

    List<AppointmentVO> getDoctorAppointments(Long doctorId, LocalDate date);

    AppointmentVO getAppointmentDetail(Long appointmentId, Long userId);

    AppointmentDTO getAppointmentSnapshot(Long appointmentId);

    PageResult<AppointmentListVO> listAll(AppointmentQueryDTO queryDTO, PageQuery pageQuery);

    Map<String, Object> getStatistics(LocalDate startDate, LocalDate endDate);
}
