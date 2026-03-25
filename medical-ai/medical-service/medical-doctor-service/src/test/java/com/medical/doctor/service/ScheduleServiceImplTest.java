package com.medical.doctor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.medical.common.redis.util.RedisUtil;
import com.medical.doctor.domain.dto.ScheduleTemplateDTO;
import com.medical.doctor.domain.entity.DoctorDepartment;
import com.medical.doctor.domain.entity.ScheduleSlot;
import com.medical.doctor.domain.entity.DoctorProfile;
import com.medical.doctor.domain.entity.ScheduleTemplate;
import com.medical.doctor.domain.vo.ScheduleSlotVO;
import com.medical.doctor.mapper.DoctorDepartmentMapper;
import com.medical.doctor.mapper.DoctorProfileMapper;
import com.medical.doctor.mapper.ScheduleSlotMapper;
import com.medical.doctor.mapper.ScheduleTemplateMapper;
import com.medical.doctor.service.impl.ScheduleServiceImpl;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceImplTest {

    @BeforeAll
    static void initMybatisMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ScheduleSlot.class);
    }

    @Mock
    private ScheduleTemplateMapper scheduleTemplateMapper;
    @Mock
    private ScheduleSlotMapper scheduleSlotMapper;
    @Mock
    private DoctorProfileMapper doctorProfileMapper;
    @Mock
    private DoctorDepartmentMapper doctorDepartmentMapper;
    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private ScheduleServiceImpl scheduleService;

    @Test
    void saveTemplate_infersPeriodWhenMissing() {
        Long doctorId = 3L;
        DoctorProfile doctor = new DoctorProfile();
        doctor.setId(doctorId);
        when(doctorProfileMapper.selectById(doctorId)).thenReturn(doctor);
        when(scheduleTemplateMapper.selectOne(any())).thenReturn(null);

        ScheduleTemplateDTO dto = new ScheduleTemplateDTO();
        dto.setDayOfWeek(1);
        dto.setStartTime(LocalTime.of(9, 0));
        dto.setEndTime(LocalTime.of(11, 30));
        dto.setMaxPatients(20);

        scheduleService.saveTemplate(doctorId, dto);

        verify(scheduleTemplateMapper).insert(argThat(periodIs("morning")));
    }

    @Test
    void deleteTemplate_shouldDeleteAvailableSlotsTogether() {
        Long templateId = 9L;
        ScheduleTemplate template = new ScheduleTemplate();
        template.setId(templateId);
        template.setDoctorId(3L);
        template.setDayOfWeek(matchingDayOfWeek(LocalDate.now()));
        template.setPeriod("morning");
        when(scheduleTemplateMapper.selectById(templateId)).thenReturn(template);

        scheduleService.deleteTemplate(templateId);

        verify(scheduleSlotMapper).delete(any());
        verify(scheduleTemplateMapper).deleteById(templateId);
        verifyDeletedCacheKeys(expectedKeysForDayOfWeek(3L, template.getDayOfWeek()));
    }

    @Test
    void getAvailableSlots_shouldReturnCachedSlotsWhenPresent() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        ScheduleSlotVO cachedSlot = new ScheduleSlotVO();
        cachedSlot.setId(8L);
        cachedSlot.setDoctorId(3L);

        when(redisUtil.get("schedule:slots:3:2026-03-10")).thenReturn(List.of(cachedSlot));

        List<ScheduleSlotVO> result = scheduleService.getAvailableSlots(3L, date);

        assertEquals(1, result.size());
        assertEquals(8L, result.get(0).getId());
        verify(scheduleSlotMapper, never()).selectList(any());
        verify(redisUtil, never()).set(any(), any(), any(Long.class), any(TimeUnit.class));
    }

    @Test
    void getAvailableSlots_shouldCacheDatabaseResult() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        ScheduleSlot slot = new ScheduleSlot();
        slot.setId(8L);
        slot.setDoctorId(3L);
        slot.setScheduleDate(date);
        slot.setPeriod("morning");
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(9, 30));
        slot.setTotalSlots(10);
        slot.setBookedSlots(2);
        slot.setStatus(0);
        DoctorProfile doctor = new DoctorProfile();
        doctor.setId(3L);
        doctor.setName("Dr. Li");

        when(redisUtil.get("schedule:slots:3:2026-03-10")).thenReturn(null);
        when(scheduleSlotMapper.selectList(any())).thenReturn(List.of(slot));
        when(doctorProfileMapper.selectById(3L)).thenReturn(doctor);

        List<ScheduleSlotVO> result = scheduleService.getAvailableSlots(3L, date);

        assertEquals(1, result.size());
        assertEquals("Dr. Li", result.get(0).getDoctorName());
        verify(redisUtil).set("schedule:slots:3:2026-03-10", result, 60L, TimeUnit.SECONDS);
    }

    @Test
    void bookSlot_shouldDeleteSlotCacheAfterSuccess() {
        ScheduleSlot slot = new ScheduleSlot();
        slot.setId(8L);
        slot.setDoctorId(3L);
        slot.setScheduleDate(LocalDate.of(2026, 3, 10));
        when(scheduleSlotMapper.selectById(8L)).thenReturn(slot);
        when(scheduleSlotMapper.update(any(), any())).thenReturn(1);

        scheduleService.bookSlot(8L);

        verify(redisUtil).delete("schedule:slots:3:2026-03-10");
    }

    @Test
    void cancelSlot_shouldDeleteSlotCacheAfterSuccess() {
        ScheduleSlot slot = new ScheduleSlot();
        slot.setId(8L);
        slot.setDoctorId(3L);
        slot.setScheduleDate(LocalDate.of(2026, 3, 10));
        when(scheduleSlotMapper.selectById(8L)).thenReturn(slot);
        when(scheduleSlotMapper.update(any(), any())).thenReturn(1);

        scheduleService.cancelSlot(8L);

        verify(redisUtil).delete("schedule:slots:3:2026-03-10");
    }

    @Test
    void saveTemplate_shouldDeleteAffectedSlotCacheKeys() {
        Long doctorId = 3L;
        int dayOfWeek = matchingDayOfWeek(LocalDate.now());
        DoctorProfile doctor = new DoctorProfile();
        doctor.setId(doctorId);
        when(doctorProfileMapper.selectById(doctorId)).thenReturn(doctor);
        when(scheduleTemplateMapper.selectOne(any())).thenReturn(null);

        ScheduleTemplateDTO dto = new ScheduleTemplateDTO();
        dto.setDayOfWeek(dayOfWeek);
        dto.setStartTime(LocalTime.of(9, 0));
        dto.setEndTime(LocalTime.of(11, 30));
        dto.setMaxPatients(20);

        scheduleService.saveTemplate(doctorId, dto);

        verify(scheduleTemplateMapper).insert(argThat(periodIs("morning")));
        verifyDeletedCacheKeys(expectedKeysForDayOfWeek(doctorId, dayOfWeek));
    }

    @Test
    void generateSlots_shouldDeleteGeneratedDateCacheKeys() {
        LocalDate startDate = LocalDate.of(2026, 3, 9);
        LocalDate endDate = LocalDate.of(2026, 3, 10);
        ScheduleTemplate template = new ScheduleTemplate();
        template.setDoctorId(3L);
        template.setDayOfWeek(matchingDayOfWeek(startDate));
        template.setPeriod("morning");
        template.setStartTime(LocalTime.of(9, 0));
        template.setEndTime(LocalTime.of(11, 0));
        template.setMaxPatients(10);
        when(scheduleTemplateMapper.selectList(any())).thenReturn(List.of(template));
        when(scheduleSlotMapper.selectCount(any())).thenReturn(0L);

        scheduleService.generateSlots(startDate, endDate);

        verify(scheduleSlotMapper).insert(any(ScheduleSlot.class));
        verifyDeletedCacheKeys(List.of("schedule:slots:3:2026-03-09"));
    }

    @Test
    void getAvailableSlotsByDepartment_shouldNotReadRedisCache() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        DoctorDepartment relation = new DoctorDepartment();
        relation.setDoctorId(3L);
        relation.setDepartmentId(9L);
        ScheduleSlot slot = new ScheduleSlot();
        slot.setId(8L);
        slot.setDoctorId(3L);
        slot.setScheduleDate(date);
        slot.setPeriod("morning");
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(9, 30));
        slot.setTotalSlots(10);
        slot.setBookedSlots(2);
        slot.setStatus(0);
        DoctorProfile doctor = new DoctorProfile();
        doctor.setId(3L);
        doctor.setName("Dr. Li");
        when(doctorDepartmentMapper.selectList(any())).thenReturn(List.of(relation));
        when(scheduleSlotMapper.selectList(any())).thenReturn(List.of(slot));
        when(doctorProfileMapper.selectBatchIds(List.of(3L))).thenReturn(List.of(doctor));

        List<ScheduleSlotVO> result = scheduleService.getAvailableSlotsByDepartment(9L, date);

        assertEquals(1, result.size());
        assertEquals("Dr. Li", result.get(0).getDoctorName());
        verify(redisUtil, never()).get(any());
        verify(redisUtil, never()).set(any(), any(), any(Long.class), any(TimeUnit.class));
    }

    private void verifyDeletedCacheKeys(List<String> expectedKeys) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(redisUtil).delete(captor.capture());
        assertEquals(expectedKeys, new ArrayList<>(captor.getValue()));
    }

    private List<String> expectedKeysForDayOfWeek(Long doctorId, Integer dayOfWeek) {
        List<String> keys = new ArrayList<>();
        LocalDate currentDate = LocalDate.now();
        LocalDate endDate = currentDate.plusDays(30);
        while (!currentDate.isAfter(endDate)) {
            if (matchingDayOfWeek(currentDate) == dayOfWeek) {
                keys.add("schedule:slots:" + doctorId + ":" + currentDate);
            }
            currentDate = currentDate.plusDays(1);
        }
        return keys;
    }

    private int matchingDayOfWeek(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek.getValue();
    }

    private ArgumentMatcher<ScheduleTemplate> periodIs(String expectedPeriod) {
        return template -> template != null && expectedPeriod.equals(template.getPeriod());
    }
}
