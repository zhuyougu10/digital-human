package com.medical.doctor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import com.medical.doctor.domain.dto.ScheduleTemplateDTO;
import com.medical.doctor.domain.entity.DoctorDepartment;
import com.medical.doctor.domain.entity.DoctorProfile;
import com.medical.doctor.domain.entity.ScheduleSlot;
import com.medical.doctor.domain.entity.ScheduleTemplate;
import com.medical.doctor.domain.vo.ScheduleSlotVO;
import com.medical.doctor.mapper.DoctorDepartmentMapper;
import com.medical.doctor.mapper.DoctorProfileMapper;
import com.medical.doctor.mapper.ScheduleSlotMapper;
import com.medical.doctor.mapper.ScheduleTemplateMapper;
import com.medical.doctor.service.ScheduleService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private final ScheduleTemplateMapper scheduleTemplateMapper;
    private final ScheduleSlotMapper scheduleSlotMapper;
    private final DoctorProfileMapper doctorProfileMapper;
    private final DoctorDepartmentMapper doctorDepartmentMapper;

    @Override
    public List<ScheduleTemplate> getTemplatesByDoctor(Long doctorId) {
        return scheduleTemplateMapper.selectList(
                new LambdaQueryWrapper<ScheduleTemplate>()
                        .eq(ScheduleTemplate::getDoctorId, doctorId)
                        .orderByAsc(ScheduleTemplate::getDayOfWeek)
                        .orderByAsc(ScheduleTemplate::getStartTime));
    }

    @Override
    public void saveTemplate(Long doctorId, ScheduleTemplateDTO dto) {
        DoctorProfile doctor = doctorProfileMapper.selectById(doctorId);
        if (doctor == null) {
            throw new BusinessException(ErrorCode.DOCTOR_NOT_FOUND);
        }
        String period = resolvePeriod(dto);
        ScheduleTemplate template = scheduleTemplateMapper.selectOne(
                new LambdaQueryWrapper<ScheduleTemplate>()
                        .eq(ScheduleTemplate::getDoctorId, doctorId)
                        .eq(ScheduleTemplate::getDayOfWeek, dto.getDayOfWeek())
                        .eq(ScheduleTemplate::getPeriod, period)
                        .last("limit 1"));
        if (template == null) {
            template = new ScheduleTemplate();
            template.setDoctorId(doctorId);
            template.setDayOfWeek(dto.getDayOfWeek());
            template.setPeriod(period);
            template.setStartTime(dto.getStartTime());
            template.setEndTime(dto.getEndTime());
            template.setMaxPatients(dto.getMaxPatients());
            template.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());
            scheduleTemplateMapper.insert(template);
            return;
        }
        template.setStartTime(dto.getStartTime());
        template.setEndTime(dto.getEndTime());
        template.setMaxPatients(dto.getMaxPatients());
        template.setStatus(dto.getStatus() == null ? template.getStatus() : dto.getStatus());
        scheduleTemplateMapper.updateById(template);
    }

    private String resolvePeriod(ScheduleTemplateDTO dto) {
        if (dto.getPeriod() != null && !dto.getPeriod().trim().isEmpty()) {
            return dto.getPeriod().trim();
        }
        LocalTime startTime = dto.getStartTime();
        if (startTime == null) {
            return "morning";
        }
        return startTime.getHour() < 12 ? "morning" : "afternoon";
    }

    @Override
    @Transactional
    public void deleteTemplate(Long templateId) {
        ScheduleTemplate template = scheduleTemplateMapper.selectById(templateId);
        if (template == null) {
            return;
        }
        scheduleSlotMapper.delete(new LambdaQueryWrapper<ScheduleSlot>()
                .eq(ScheduleSlot::getDoctorId, template.getDoctorId())
                .eq(ScheduleSlot::getPeriod, template.getPeriod())
                .eq(ScheduleSlot::getBookedSlots, 0));
        scheduleSlotMapper.update(null, new UpdateWrapper<ScheduleSlot>()
                .set("status", 1)
                .eq("doctor_id", template.getDoctorId())
                .eq("period", template.getPeriod())
                .gt("booked_slots", 0));
        scheduleTemplateMapper.deleteById(templateId);
    }

    @Override
    @Transactional
    public void generateSlots(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Invalid date range");
        }
        List<ScheduleTemplate> templates = scheduleTemplateMapper.selectList(
                new LambdaQueryWrapper<ScheduleTemplate>()
                        .eq(ScheduleTemplate::getStatus, 0));
        if (templates.isEmpty()) {
            return;
        }
        Map<Integer, List<ScheduleTemplate>> byDay = templates.stream()
                .collect(Collectors.groupingBy(ScheduleTemplate::getDayOfWeek));

        LocalDate date = startDate;
        while (!date.isAfter(endDate)) {
            int day = convertDayOfWeek(date.getDayOfWeek());
            List<ScheduleTemplate> dayTemplates = byDay.get(day);
            if (dayTemplates != null && !dayTemplates.isEmpty()) {
                for (ScheduleTemplate template : dayTemplates) {
                    Long exists = scheduleSlotMapper.selectCount(
                            new LambdaQueryWrapper<ScheduleSlot>()
                                    .eq(ScheduleSlot::getDoctorId, template.getDoctorId())
                                    .eq(ScheduleSlot::getScheduleDate, date)
                                    .eq(ScheduleSlot::getPeriod, template.getPeriod()));
                    if (exists != null && exists > 0) {
                        continue;
                    }
                    ScheduleSlot slot = new ScheduleSlot();
                    slot.setDoctorId(template.getDoctorId());
                    slot.setScheduleDate(date);
                    slot.setPeriod(template.getPeriod());
                    slot.setStartTime(template.getStartTime());
                    slot.setEndTime(template.getEndTime());
                    slot.setTotalSlots(template.getMaxPatients());
                    slot.setBookedSlots(0);
                    slot.setStatus(0);
                    scheduleSlotMapper.insert(slot);
                }
            }
            date = date.plusDays(1);
        }
    }

    @Override
    public List<ScheduleSlotVO> getAvailableSlots(Long doctorId, LocalDate date) {
        List<ScheduleSlot> slots = scheduleSlotMapper.selectList(
                new LambdaQueryWrapper<ScheduleSlot>()
                        .eq(ScheduleSlot::getDoctorId, doctorId)
                        .eq(ScheduleSlot::getScheduleDate, date)
                        .eq(ScheduleSlot::getStatus, 0)
                        .orderByAsc(ScheduleSlot::getStartTime));
        if (slots.isEmpty()) {
            return Collections.emptyList();
        }
        DoctorProfile doctor = doctorProfileMapper.selectById(doctorId);
        String doctorName = doctor == null ? "" : doctor.getName();
        return slots.stream().map(slot -> toSlotVO(slot, doctorName)).collect(Collectors.toList());
    }

    @Override
    public List<ScheduleSlotVO> getAvailableSlotsByDepartment(Long departmentId, LocalDate date) {
        List<Long> doctorIds = doctorDepartmentMapper.selectList(
                        new LambdaQueryWrapper<DoctorDepartment>()
                                .eq(DoctorDepartment::getDepartmentId, departmentId))
                .stream()
                .map(DoctorDepartment::getDoctorId)
                .distinct()
                .collect(Collectors.toList());
        if (doctorIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<ScheduleSlot> slots = scheduleSlotMapper.selectList(
                new LambdaQueryWrapper<ScheduleSlot>()
                        .in(ScheduleSlot::getDoctorId, doctorIds)
                        .eq(ScheduleSlot::getScheduleDate, date)
                        .eq(ScheduleSlot::getStatus, 0)
                        .orderByAsc(ScheduleSlot::getDoctorId)
                        .orderByAsc(ScheduleSlot::getStartTime));
        if (slots.isEmpty()) {
            return Collections.emptyList();
        }
        List<DoctorProfile> doctors = doctorProfileMapper.selectBatchIds(doctorIds);
        Map<Long, String> doctorNameMap = doctors.stream()
                .collect(Collectors.toMap(DoctorProfile::getId, DoctorProfile::getName, (a, b) -> a));
        return slots.stream()
                .map(slot -> toSlotVO(slot, doctorNameMap.getOrDefault(slot.getDoctorId(), "")))
                .collect(Collectors.toList());
    }

    @Override
    public boolean bookSlot(Long slotId) {
        int rows = scheduleSlotMapper.update(null, new LambdaUpdateWrapper<ScheduleSlot>()
                .setSql("booked_slots = booked_slots + 1, status = IF(booked_slots + 1 >= total_slots, 1, 0)")
                .eq(ScheduleSlot::getId, slotId)
                .apply("booked_slots < total_slots")
                .eq(ScheduleSlot::getStatus, 0));
        return rows > 0;
    }

    @Override
    public boolean cancelSlot(Long slotId) {
        int rows = scheduleSlotMapper.update(null, new LambdaUpdateWrapper<ScheduleSlot>()
                .setSql("booked_slots = booked_slots - 1")
                .set(ScheduleSlot::getStatus, 0)
                .eq(ScheduleSlot::getId, slotId)
                .gt(ScheduleSlot::getBookedSlots, 0));
        return rows > 0;
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void autoGenerateSlots() {
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = start.plusDays(6);
        log.info("Auto generate schedule slots: {} -> {}", start, end);
        generateSlots(start, end);
    }

    private int convertDayOfWeek(DayOfWeek dayOfWeek) {
        return dayOfWeek.getValue();
    }

    private ScheduleSlotVO toSlotVO(ScheduleSlot slot, String doctorName) {
        ScheduleSlotVO vo = new ScheduleSlotVO();
        vo.setId(slot.getId());
        vo.setDoctorId(slot.getDoctorId());
        vo.setDoctorName(doctorName);
        vo.setScheduleDate(slot.getScheduleDate());
        vo.setPeriod(slot.getPeriod());
        vo.setStartTime(slot.getStartTime());
        vo.setEndTime(slot.getEndTime());
        vo.setTotalSlots(slot.getTotalSlots());
        vo.setBookedSlots(slot.getBookedSlots());
        vo.setAvailableSlots(slot.getTotalSlots() - slot.getBookedSlots());
        vo.setStatus(slot.getStatus());
        return vo;
    }
}
