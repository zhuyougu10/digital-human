package com.medical.appointment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medical.api.doctor.RemoteDoctorService;
import com.medical.api.doctor.RemoteScheduleService;
import com.medical.api.doctor.dto.DoctorInfoDTO;
import com.medical.api.doctor.dto.SlotInfoDTO;
import com.medical.api.user.RemoteUserService;
import com.medical.api.user.dto.UserInfoDTO;
import com.medical.appointment.domain.dto.AppointmentQueryDTO;
import com.medical.appointment.domain.dto.CreateAppointmentDTO;
import com.medical.appointment.domain.entity.Appointment;
import com.medical.appointment.domain.vo.AppointmentListVO;
import com.medical.appointment.domain.vo.AppointmentVO;
import com.medical.appointment.mapper.AppointmentMapper;
import com.medical.appointment.service.AppointmentService;
import com.medical.common.core.domain.PageQuery;
import com.medical.common.core.domain.PageResult;
import com.medical.common.core.domain.R;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_CANCELLED = 2;
    private static final int MAX_SLOT_LOOKAHEAD_DAYS = 30;

    private final AppointmentMapper appointmentMapper;
    private final RemoteDoctorService remoteDoctorService;
    private final RemoteScheduleService remoteScheduleService;
    private final RemoteUserService remoteUserService;

    @Override
    @Transactional
    public Long createAppointment(CreateAppointmentDTO dto) {
        if (dto == null || dto.getPatientId() == null || dto.getDoctorId() == null
                || dto.getDepartmentId() == null || dto.getSlotId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        Long duplicateCount = appointmentMapper.selectCount(
                new LambdaQueryWrapper<Appointment>()
                        .eq(Appointment::getPatientId, dto.getPatientId())
                        .eq(Appointment::getSlotId, dto.getSlotId())
                        .eq(Appointment::getDeleted, 0));
        if (duplicateCount != null && duplicateCount > 0) {
            throw new BusinessException(ErrorCode.APPOINTMENT_ALREADY_EXISTS);
        }

        R<DoctorInfoDTO> doctorResp = remoteDoctorService.getDoctorById(dto.getDoctorId());
        if (doctorResp == null || !doctorResp.isSuccess() || doctorResp.getData() == null) {
            throw new BusinessException(ErrorCode.DOCTOR_NOT_FOUND);
        }

        SlotInfoDTO slotInfo = findSlotInfo(dto.getDoctorId(), dto.getSlotId());

        R<Boolean> lockResp = remoteScheduleService.bookSlot(dto.getSlotId());
        if (lockResp == null || !lockResp.isSuccess() || !Boolean.TRUE.equals(lockResp.getData())) {
            throw new BusinessException(ErrorCode.SLOT_NOT_AVAILABLE);
        }

        Long currentCount = appointmentMapper.selectCount(
                new LambdaQueryWrapper<Appointment>()
                        .eq(Appointment::getSlotId, dto.getSlotId())
                        .eq(Appointment::getDeleted, 0));

        Appointment appointment = new Appointment();
        appointment.setPatientId(dto.getPatientId());
        appointment.setDoctorId(dto.getDoctorId());
        appointment.setDepartmentId(dto.getDepartmentId());
        appointment.setSlotId(dto.getSlotId());
        appointment.setSessionId(dto.getSessionId());
        appointment.setAppointmentDate(slotInfo == null ? LocalDate.now() : slotInfo.getScheduleDate());
        appointment.setPeriod(slotInfo == null ? "morning" : slotInfo.getPeriod());
        appointment.setStartTime(slotInfo == null ? LocalTime.of(9, 0) : slotInfo.getStartTime());
        appointment.setEndTime(slotInfo == null ? LocalTime.of(9, 30) : slotInfo.getEndTime());
        appointment.setQueueNumber((int) ((currentCount == null ? 0 : currentCount) + 1));
        appointment.setStatus(STATUS_PENDING);
        appointmentMapper.insert(appointment);

        return appointment.getId();
    }

    @Override
    @Transactional
    public void cancelAppointment(Long appointmentId, Long userId) {
        Appointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null || Objects.equals(appointment.getDeleted(), 1)) {
            throw new BusinessException(ErrorCode.APPOINTMENT_NOT_FOUND);
        }
        if (!Objects.equals(appointment.getPatientId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (Objects.equals(appointment.getStatus(), STATUS_CANCELLED)) {
            return;
        }

        R<Boolean> cancelResp = remoteScheduleService.cancelSlot(appointment.getSlotId());
        if (cancelResp == null || !cancelResp.isSuccess() || !Boolean.TRUE.equals(cancelResp.getData())) {
            throw new BusinessException(ErrorCode.APPOINTMENT_CANCEL_FAIL);
        }

        appointment.setStatus(STATUS_CANCELLED);
        appointment.setCancelReason("cancelled by patient");
        appointmentMapper.updateById(appointment);
    }

    @Override
    public PageResult<AppointmentListVO> getMyAppointments(Long userId, PageQuery pageQuery) {
        Page<Appointment> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        Page<Appointment> result = appointmentMapper.selectPage(
                page,
                new LambdaQueryWrapper<Appointment>()
                        .eq(Appointment::getPatientId, userId)
                        .eq(Appointment::getDeleted, 0)
                        .orderByDesc(Appointment::getCreateTime));
        List<AppointmentListVO> records = result.getRecords().stream()
                .map(this::toListVO)
                .collect(Collectors.toList());
        return PageResult.of(records, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    public List<AppointmentVO> getDoctorAppointments(Long doctorId, LocalDate date) {
        List<Appointment> appointments = appointmentMapper.selectList(
                new LambdaQueryWrapper<Appointment>()
                        .eq(Appointment::getDoctorId, doctorId)
                        .eq(date != null, Appointment::getAppointmentDate, date)
                        .eq(Appointment::getDeleted, 0)
                        .orderByAsc(Appointment::getStartTime));
        return appointments.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public AppointmentVO getAppointmentDetail(Long appointmentId, Long userId) {
        Appointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null || Objects.equals(appointment.getDeleted(), 1)) {
            throw new BusinessException(ErrorCode.APPOINTMENT_NOT_FOUND);
        }
        if (!Objects.equals(appointment.getPatientId(), userId) && !Objects.equals(appointment.getDoctorId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return toVO(appointment);
    }

    @Override
    public PageResult<AppointmentListVO> listAll(AppointmentQueryDTO queryDTO, PageQuery pageQuery) {
        LambdaQueryWrapper<Appointment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(queryDTO != null && queryDTO.getPatientId() != null, Appointment::getPatientId, queryDTO.getPatientId())
                .eq(queryDTO != null && queryDTO.getDoctorId() != null, Appointment::getDoctorId, queryDTO.getDoctorId())
                .eq(queryDTO != null && queryDTO.getDate() != null, Appointment::getAppointmentDate, queryDTO.getDate())
                .eq(queryDTO != null && queryDTO.getStatus() != null, Appointment::getStatus, queryDTO.getStatus())
                .eq(Appointment::getDeleted, 0)
                .orderByDesc(Appointment::getCreateTime);
        if (queryDTO != null) {
            if (queryDTO.getStartDate() != null && queryDTO.getEndDate() != null) {
                wrapper.between(Appointment::getAppointmentDate, queryDTO.getStartDate(), queryDTO.getEndDate());
            } else if (queryDTO.getStartDate() != null) {
                wrapper.ge(Appointment::getAppointmentDate, queryDTO.getStartDate());
            } else if (queryDTO.getEndDate() != null) {
                wrapper.le(Appointment::getAppointmentDate, queryDTO.getEndDate());
            }
        }

        Page<Appointment> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        Page<Appointment> result = appointmentMapper.selectPage(page, wrapper);
        List<AppointmentListVO> records = result.getRecords().stream()
                .map(this::toListVO)
                .collect(Collectors.toList());
        return PageResult.of(records, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    public Map<String, Object> getStatistics(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now();
        Long todayCount = appointmentMapper.selectCount(
                new LambdaQueryWrapper<Appointment>()
                        .eq(Appointment::getAppointmentDate, today)
                        .eq(Appointment::getDeleted, 0));

        LocalDate trendStart = startDate == null ? today.minusDays(6) : startDate;
        LocalDate trendEnd = endDate == null ? today : endDate;

        List<Appointment> rangeAppointments = appointmentMapper.selectList(
                new LambdaQueryWrapper<Appointment>()
                        .between(Appointment::getAppointmentDate, trendStart, trendEnd)
                        .eq(Appointment::getDeleted, 0));
        Map<LocalDate, Long> grouped = rangeAppointments.stream()
                .collect(Collectors.groupingBy(Appointment::getAppointmentDate, Collectors.counting()));

        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDate cursor = trendStart;
        while (!cursor.isAfter(trendEnd)) {
            Map<String, Object> dayData = new LinkedHashMap<>();
            dayData.put("date", cursor);
            dayData.put("count", grouped.getOrDefault(cursor, 0L));
            trend.add(dayData);
            cursor = cursor.plusDays(1);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("todayCount", todayCount == null ? 0L : todayCount);
        result.put("trend", trend);
        return result;
    }

    private SlotInfoDTO findSlotInfo(Long doctorId, Long slotId) {
        LocalDate cursor = LocalDate.now();
        for (int i = 0; i <= MAX_SLOT_LOOKAHEAD_DAYS; i++) {
            R<List<SlotInfoDTO>> slotsResp = remoteScheduleService.getAvailableSlots(doctorId, cursor.toString());
            if (slotsResp != null && slotsResp.isSuccess() && slotsResp.getData() != null) {
                for (SlotInfoDTO slot : slotsResp.getData()) {
                    if (Objects.equals(slot.getId(), slotId)) {
                        return slot;
                    }
                }
            }
            cursor = cursor.plusDays(1);
        }
        return null;
    }

    private AppointmentListVO toListVO(Appointment appointment) {
        AppointmentListVO vo = new AppointmentListVO();
        vo.setId(appointment.getId());
        vo.setPatientId(appointment.getPatientId());
        UserInfoDTO userInfo = fetchUserInfo(appointment.getPatientId());
        if (userInfo != null) {
            vo.setPatientName(userInfo.getNickname() == null || userInfo.getNickname().isBlank()
                    ? userInfo.getUsername() : userInfo.getNickname());
            vo.setPatientPhone(userInfo.getPhone());
        }
        vo.setDoctorId(appointment.getDoctorId());
        vo.setAppointmentDate(appointment.getAppointmentDate());
        vo.setPeriod(appointment.getPeriod());
        vo.setStartTime(appointment.getStartTime());
        vo.setEndTime(appointment.getEndTime());
        vo.setStatus(appointment.getStatus());

        DoctorInfoDTO doctorInfo = fetchDoctorInfo(appointment.getDoctorId());
        if (doctorInfo != null) {
            vo.setDoctorName(doctorInfo.getName());
            vo.setDepartmentName(doctorInfo.getDepartmentNames());
        }
        return vo;
    }

    private AppointmentVO toVO(Appointment appointment) {
        AppointmentVO vo = new AppointmentVO();
        vo.setId(appointment.getId());
        vo.setPatientId(appointment.getPatientId());
        vo.setDoctorId(appointment.getDoctorId());
        vo.setDepartmentId(appointment.getDepartmentId());
        vo.setSlotId(appointment.getSlotId());
        vo.setSessionId(appointment.getSessionId());
        vo.setAppointmentDate(appointment.getAppointmentDate());
        vo.setPeriod(appointment.getPeriod());
        vo.setStartTime(appointment.getStartTime());
        vo.setEndTime(appointment.getEndTime());
        vo.setQueueNumber(appointment.getQueueNumber());
        vo.setStatus(appointment.getStatus());
        vo.setCancelReason(appointment.getCancelReason());
        vo.setCreateTime(appointment.getCreateTime());
        vo.setUpdateTime(appointment.getUpdateTime());

        DoctorInfoDTO doctorInfo = fetchDoctorInfo(appointment.getDoctorId());
        if (doctorInfo != null) {
            vo.setDoctorName(doctorInfo.getName());
            vo.setDepartmentName(doctorInfo.getDepartmentNames());
        } else {
            vo.setDepartmentName("");
        }

        vo.setConversationSummary(null);
        return vo;
    }

    private DoctorInfoDTO fetchDoctorInfo(Long doctorId) {
        if (doctorId == null) {
            return null;
        }
        R<DoctorInfoDTO> doctorResp = remoteDoctorService.getDoctorById(doctorId);
        if (doctorResp == null || !doctorResp.isSuccess()) {
            return null;
        }
        return doctorResp.getData();
    }

    private UserInfoDTO fetchUserInfo(Long userId) {
        if (userId == null) {
            return null;
        }
        R<UserInfoDTO> userResp = remoteUserService.getUserById(userId);
        if (userResp == null || !userResp.isSuccess()) {
            return null;
        }
        return userResp.getData();
    }
}
