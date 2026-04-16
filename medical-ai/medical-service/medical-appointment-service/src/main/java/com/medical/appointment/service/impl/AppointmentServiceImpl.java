package com.medical.appointment.service.impl;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medical.api.doctor.RemoteDoctorService;
import com.medical.api.doctor.RemoteScheduleService;
import com.medical.api.doctor.dto.DoctorInfoDTO;
import com.medical.api.doctor.dto.SlotInfoDTO;
import com.medical.api.user.RemoteUserService;
import com.medical.api.user.dto.UserInfoDTO;
import com.medical.appointment.constant.AppointmentCacheConstants;
import com.medical.appointment.domain.dto.AppointmentQueryDTO;
import com.medical.appointment.domain.dto.CreateAppointmentDTO;
import com.medical.appointment.domain.entity.Appointment;
import com.medical.appointment.domain.vo.AppointmentListVO;
import com.medical.appointment.domain.vo.AppointmentVO;
import com.medical.appointment.mapper.AppointmentMapper;
import com.medical.appointment.service.AppointmentEventOutboxService;
import com.medical.appointment.service.AppointmentService;
import com.medical.common.core.domain.PageQuery;
import com.medical.common.core.domain.PageResult;
import com.medical.common.core.domain.R;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import com.medical.common.redis.util.RedisUtil;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    public static final String CREATE_RESOURCE = "svc:appointment:create";
    public static final String CREATE_BUSY_MESSAGE = "当前号源繁忙，请刷新后重试";
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_CANCELLED = 2;
    private static final int MAX_SLOT_LOOKAHEAD_DAYS = 30;

    private final AppointmentMapper appointmentMapper;
    private final RemoteDoctorService remoteDoctorService;
    private final RemoteScheduleService remoteScheduleService;
    private final RemoteUserService remoteUserService;
    private final AppointmentEventOutboxService appointmentEventOutboxService;
    private final RedisUtil redisUtil;

    @Override
    @GlobalTransactional(name = "createAppointment", rollbackFor = Exception.class)
    @Transactional
    public Long createAppointment(CreateAppointmentDTO dto) {
        final Entry sentinelEntry;
        try {
            Object slotParam = dto == null ? null : dto.getSlotId();
            sentinelEntry = SphU.entry(CREATE_RESOURCE, EntryType.IN, 1, slotParam);
        } catch (BlockException e) {
            throw new BusinessException(ErrorCode.FAIL, CREATE_BUSY_MESSAGE);
        }

        try {
            if (dto == null || dto.getPatientId() == null || dto.getDoctorId() == null
                    || dto.getDepartmentId() == null || dto.getSlotId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }

            String dedupKey = buildDedupKey(dto.getPatientId(), dto.getSlotId());
            Boolean locked = redisUtil.setIfAbsent(
                    dedupKey,
                    1,
                    AppointmentCacheConstants.APPOINTMENT_DEDUP_TTL_SECONDS,
                    TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(locked)) {
                throw new BusinessException(ErrorCode.FAIL, "请勿重复提交预约请求");
            }

            boolean success = false;
            try {
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
                if (slotInfo == null) {
                    throw new BusinessException(ErrorCode.SLOT_NOT_AVAILABLE);
                }

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
                appointment.setAppointmentDate(slotInfo.getScheduleDate());
                appointment.setPeriod(slotInfo.getPeriod());
                appointment.setStartTime(slotInfo.getStartTime());
                appointment.setEndTime(slotInfo.getEndTime());
                appointment.setQueueNumber((int) ((currentCount == null ? 0 : currentCount) + 1));
                appointment.setStatus(STATUS_PENDING);
                appointmentMapper.insert(appointment);
                appointmentEventOutboxService.saveCreatedEvent(appointment);
                success = true;
                return appointment.getId();
            } finally {
                if (!success) {
                    redisUtil.delete(dedupKey);
                }
            }
        } finally {
            sentinelEntry.exit();
        }
    }

    @Override
    @GlobalTransactional(name = "cancelAppointment", rollbackFor = Exception.class)
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
        appointmentEventOutboxService.saveCancelledEvent(appointment);
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
        Map<Long, DoctorInfoDTO> doctorMap = batchFetchDoctorInfo(collectDoctorIds(result.getRecords()));
        Map<Long, UserInfoDTO> userMap = batchFetchUserInfo(collectPatientIds(result.getRecords()));
        List<AppointmentListVO> records = result.getRecords().stream()
                .map(appointment -> toListVO(appointment, doctorMap, userMap))
                .collect(Collectors.toList());
        return PageResult.of(records, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    public List<AppointmentVO> getDoctorAppointments(Long doctorUserId, LocalDate date) {
        DoctorInfoDTO currentDoctor = fetchDoctorInfoByUserId(doctorUserId);
        if (currentDoctor == null || currentDoctor.getId() == null) {
            throw new BusinessException(ErrorCode.DOCTOR_NOT_FOUND);
        }

        List<Appointment> appointments = appointmentMapper.selectList(
                new LambdaQueryWrapper<Appointment>()
                        .eq(Appointment::getDoctorId, currentDoctor.getId())
                        .eq(date != null, Appointment::getAppointmentDate, date)
                        .eq(Appointment::getDeleted, 0)
                        .orderByAsc(Appointment::getStartTime));
        Map<Long, DoctorInfoDTO> doctorMap = batchFetchDoctorInfo(collectDoctorIds(appointments));
        return appointments.stream()
                .map(appointment -> toVO(appointment, doctorMap))
                .collect(Collectors.toList());
    }

    @Override
    public AppointmentVO getAppointmentDetail(Long appointmentId, Long userId) {
        Appointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null || Objects.equals(appointment.getDeleted(), 1)) {
            throw new BusinessException(ErrorCode.APPOINTMENT_NOT_FOUND);
        }
        boolean isPatientOwner = Objects.equals(appointment.getPatientId(), userId);
        boolean isDoctorOwner = isAppointmentDoctorOwner(appointment, userId);
        if (!isPatientOwner && !isDoctorOwner) {
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
        Map<Long, DoctorInfoDTO> doctorMap = batchFetchDoctorInfo(collectDoctorIds(result.getRecords()));
        Map<Long, UserInfoDTO> userMap = batchFetchUserInfo(collectPatientIds(result.getRecords()));
        List<AppointmentListVO> records = result.getRecords().stream()
                .map(appointment -> toListVO(appointment, doctorMap, userMap))
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
                    if (Objects.equals(slot.getId(), slotId)
                            && Objects.equals(slot.getDoctorId(), doctorId)) {
                        return slot;
                    }
                }
            }
            cursor = cursor.plusDays(1);
        }
        return null;
    }

    private AppointmentListVO toListVO(Appointment appointment) {
        return toListVO(
                appointment,
                batchFetchDoctorInfo(Collections.singleton(appointment.getDoctorId())),
                batchFetchUserInfo(Collections.singleton(appointment.getPatientId())));
    }

    private AppointmentListVO toListVO(Appointment appointment,
                                       Map<Long, DoctorInfoDTO> doctorMap,
                                       Map<Long, UserInfoDTO> userMap) {
        AppointmentListVO vo = new AppointmentListVO();
        vo.setId(appointment.getId());
        vo.setPatientId(appointment.getPatientId());
        UserInfoDTO userInfo = userMap.get(appointment.getPatientId());
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

        DoctorInfoDTO doctorInfo = doctorMap.get(appointment.getDoctorId());
        if (doctorInfo != null) {
            vo.setDoctorName(doctorInfo.getName());
            vo.setDepartmentName(doctorInfo.getDepartmentNames());
        }
        return vo;
    }

    private AppointmentVO toVO(Appointment appointment) {
        return toVO(appointment, batchFetchDoctorInfo(Collections.singleton(appointment.getDoctorId())));
    }

    private AppointmentVO toVO(Appointment appointment, Map<Long, DoctorInfoDTO> doctorMap) {
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

        DoctorInfoDTO doctorInfo = doctorMap.get(appointment.getDoctorId());
        if (doctorInfo != null) {
            vo.setDoctorName(doctorInfo.getName());
            vo.setDepartmentName(doctorInfo.getDepartmentNames());
        } else {
            vo.setDepartmentName("");
        }

        vo.setConversationSummary(null);
        return vo;
    }

    private Set<Long> collectDoctorIds(List<Appointment> appointments) {
        return appointments.stream()
                .map(Appointment::getDoctorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Set<Long> collectPatientIds(List<Appointment> appointments) {
        return appointments.stream()
                .map(Appointment::getPatientId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Map<Long, DoctorInfoDTO> batchFetchDoctorInfo(Set<Long> ids) {
        Map<Long, DoctorInfoDTO> map = new HashMap<>();
        for (Long id : ids) {
            DoctorInfoDTO info = fetchDoctorInfo(id);
            if (info != null) {
                map.put(id, info);
            }
        }
        return map;
    }

    private Map<Long, UserInfoDTO> batchFetchUserInfo(Set<Long> ids) {
        Map<Long, UserInfoDTO> map = new HashMap<>();
        for (Long id : ids) {
            UserInfoDTO info = fetchUserInfo(id);
            if (info != null) {
                map.put(id, info);
            }
        }
        return map;
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

    private DoctorInfoDTO fetchDoctorInfoByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        R<DoctorInfoDTO> doctorResp = remoteDoctorService.getDoctorByUserId(userId);
        if (doctorResp == null || !doctorResp.isSuccess()) {
            return null;
        }
        return doctorResp.getData();
    }

    private boolean isAppointmentDoctorOwner(Appointment appointment, Long userId) {
        if (appointment == null || appointment.getDoctorId() == null || userId == null) {
            return false;
        }
        DoctorInfoDTO doctorInfo = fetchDoctorInfo(appointment.getDoctorId());
        return doctorInfo != null && Objects.equals(doctorInfo.getUserId(), userId);
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

    private String buildDedupKey(Long patientId, Long slotId) {
        return AppointmentCacheConstants.APPOINTMENT_DEDUP_KEY_PREFIX + patientId + ":" + slotId;
    }
}
