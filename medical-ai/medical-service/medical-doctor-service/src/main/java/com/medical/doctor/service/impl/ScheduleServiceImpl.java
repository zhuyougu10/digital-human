package com.medical.doctor.service.impl;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import com.medical.common.redis.util.RedisUtil;
import com.medical.doctor.constant.DoctorCacheConstants;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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

    public static final String SLOTS_RESOURCE = "svc:doctor:scheduleSlots";
    public static final String SLOTS_BUSY_MESSAGE = "号源查询繁忙，请稍后重试";
    private final ScheduleTemplateMapper scheduleTemplateMapper;
    private final ScheduleSlotMapper scheduleSlotMapper;
    private final DoctorProfileMapper doctorProfileMapper;
    private final DoctorDepartmentMapper doctorDepartmentMapper;
    private final RedisUtil redisUtil;

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
            invalidateTemplateSlotCache(doctorId, dto.getDayOfWeek());
            return;
        }
        template.setStartTime(dto.getStartTime());
        template.setEndTime(dto.getEndTime());
        template.setMaxPatients(dto.getMaxPatients());
        template.setStatus(dto.getStatus() == null ? template.getStatus() : dto.getStatus());
        scheduleTemplateMapper.updateById(template);
        invalidateTemplateSlotCache(doctorId, dto.getDayOfWeek());
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
        invalidateTemplateSlotCache(template.getDoctorId(), template.getDayOfWeek());
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
        Set<String> cacheKeysToDelete = new LinkedHashSet<>();

        LocalDate date = startDate;
        while (!date.isAfter(endDate)) {
            int day = convertDayOfWeek(date.getDayOfWeek());
            List<ScheduleTemplate> dayTemplates = byDay.get(day);
            if (dayTemplates != null && !dayTemplates.isEmpty()) {
                for (ScheduleTemplate template : dayTemplates) {
                    cacheKeysToDelete.add(buildScheduleSlotsCacheKey(template.getDoctorId(), date));
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
        deleteScheduleSlotCache(cacheKeysToDelete);
    }

    @Override
    @Transactional
    public List<ScheduleSlotVO> getAvailableSlots(Long doctorId, LocalDate date) {
        final Entry sentinelEntry;
        try {
            sentinelEntry = SphU.entry(SLOTS_RESOURCE, EntryType.IN, 1, doctorId + ":" + date);
        } catch (BlockException e) {
            throw new BusinessException(ErrorCode.FAIL, SLOTS_BUSY_MESSAGE);
        }

        try {
            String cacheKey = buildScheduleSlotsCacheKey(doctorId, date);
            List<ScheduleSlotVO> cachedSlots = getCachedSlots(cacheKey);
            if (cachedSlots != null) {
                return cachedSlots;
            }

            List<ScheduleSlot> slots = scheduleSlotMapper.selectList(
                    new LambdaQueryWrapper<ScheduleSlot>()
                            .eq(ScheduleSlot::getDoctorId, doctorId)
                            .eq(ScheduleSlot::getScheduleDate, date)
                            .eq(ScheduleSlot::getStatus, 0)
                            .orderByAsc(ScheduleSlot::getStartTime));
            if (slots.isEmpty()) {
                slots = materializeSlotsFromTemplatesIfMissing(doctorId, date);
                if (slots.isEmpty()) {
                    redisUtil.set(cacheKey, Collections.emptyList(),
                            DoctorCacheConstants.SCHEDULE_SLOTS_TTL_SECONDS, TimeUnit.SECONDS);
                    return Collections.emptyList();
                }
            }
            DoctorProfile doctor = doctorProfileMapper.selectById(doctorId);
            String doctorName = doctor == null ? "" : doctor.getName();
            List<ScheduleSlotVO> result = slots.stream()
                    .map(slot -> toSlotVO(slot, doctorName))
                    .collect(Collectors.toList());
            redisUtil.set(cacheKey, result, DoctorCacheConstants.SCHEDULE_SLOTS_TTL_SECONDS, TimeUnit.SECONDS);
            return result;
        } finally {
            sentinelEntry.exit();
        }
    }

    private List<ScheduleSlot> materializeSlotsFromTemplatesIfMissing(Long doctorId, LocalDate date) {
        List<ScheduleSlot> existingSlots = scheduleSlotMapper.selectList(
                new LambdaQueryWrapper<ScheduleSlot>()
                        .eq(ScheduleSlot::getDoctorId, doctorId)
                        .eq(ScheduleSlot::getScheduleDate, date)
                        .orderByAsc(ScheduleSlot::getStartTime));
        if (!existingSlots.isEmpty()) {
            return Collections.emptyList();
        }

        int dayOfWeek = convertDayOfWeek(date.getDayOfWeek());
        List<ScheduleTemplate> templates = scheduleTemplateMapper.selectList(
                new LambdaQueryWrapper<ScheduleTemplate>()
                        .eq(ScheduleTemplate::getDoctorId, doctorId)
                        .eq(ScheduleTemplate::getDayOfWeek, dayOfWeek)
                        .eq(ScheduleTemplate::getStatus, 0)
                        .orderByAsc(ScheduleTemplate::getStartTime));
        if (templates.isEmpty()) {
            return Collections.emptyList();
        }

        List<ScheduleSlot> generatedSlots = new ArrayList<>();
        for (ScheduleTemplate template : templates) {
            ScheduleSlot slot = new ScheduleSlot();
            slot.setDoctorId(template.getDoctorId());
            slot.setScheduleDate(date);
            slot.setPeriod(template.getPeriod());
            slot.setStartTime(template.getStartTime());
            slot.setEndTime(template.getEndTime());
            slot.setTotalSlots(template.getMaxPatients() == null ? 0 : template.getMaxPatients());
            slot.setBookedSlots(0);
            slot.setStatus(0);
            scheduleSlotMapper.insert(slot);
            generatedSlots.add(slot);
        }
        return generatedSlots;
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
        ScheduleSlot slot = scheduleSlotMapper.selectById(slotId);
        int rows = scheduleSlotMapper.update(null, new LambdaUpdateWrapper<ScheduleSlot>()
                .setSql("booked_slots = booked_slots + 1, status = IF(booked_slots + 1 >= total_slots, 1, 0)")
                .eq(ScheduleSlot::getId, slotId)
                .apply("booked_slots < total_slots")
                .eq(ScheduleSlot::getStatus, 0));
        if (rows > 0) {
            invalidateSlotCache(slot);
        }
        return rows > 0;
    }

    @Override
    public boolean cancelSlot(Long slotId) {
        ScheduleSlot slot = scheduleSlotMapper.selectById(slotId);
        int rows = scheduleSlotMapper.update(null, new LambdaUpdateWrapper<ScheduleSlot>()
                .setSql("booked_slots = booked_slots - 1")
                .set(ScheduleSlot::getStatus, 0)
                .eq(ScheduleSlot::getId, slotId)
                .gt(ScheduleSlot::getBookedSlots, 0));
        if (rows > 0) {
            invalidateSlotCache(slot);
        }
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

    @SuppressWarnings("unchecked")
    private List<ScheduleSlotVO> getCachedSlots(String cacheKey) {
        return redisUtil.get(cacheKey);
    }

    private void invalidateSlotCache(ScheduleSlot slot) {
        if (slot == null || slot.getDoctorId() == null || slot.getScheduleDate() == null) {
            return;
        }
        redisUtil.delete(buildScheduleSlotsCacheKey(slot.getDoctorId(), slot.getScheduleDate()));
    }

    private void invalidateTemplateSlotCache(Long doctorId, Integer dayOfWeek) {
        if (doctorId == null || dayOfWeek == null) {
            return;
        }
        LocalDate currentDate = LocalDate.now();
        LocalDate endDate = currentDate.plusDays(DoctorCacheConstants.SCHEDULE_TEMPLATE_INVALIDATE_DAYS);
        List<String> keys = new ArrayList<>();
        while (!currentDate.isAfter(endDate)) {
            if (convertDayOfWeek(currentDate.getDayOfWeek()) == dayOfWeek) {
                keys.add(buildScheduleSlotsCacheKey(doctorId, currentDate));
            }
            currentDate = currentDate.plusDays(1);
        }
        deleteScheduleSlotCache(keys);
    }

    private void deleteScheduleSlotCache(Iterable<String> keys) {
        List<String> keyList = new ArrayList<>();
        for (String key : keys) {
            if (key != null && !key.isBlank()) {
                keyList.add(key);
            }
        }
        if (!keyList.isEmpty()) {
            redisUtil.delete(keyList);
        }
    }

    private String buildScheduleSlotsCacheKey(Long doctorId, LocalDate date) {
        return DoctorCacheConstants.SCHEDULE_SLOTS_KEY_PREFIX + doctorId + ":" + date;
    }
}
