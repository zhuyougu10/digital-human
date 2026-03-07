package com.medical.doctor.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medical.doctor.domain.dto.ScheduleTemplateDTO;
import com.medical.doctor.domain.entity.DoctorProfile;
import com.medical.doctor.domain.entity.ScheduleTemplate;
import com.medical.doctor.mapper.DoctorDepartmentMapper;
import com.medical.doctor.mapper.DoctorProfileMapper;
import com.medical.doctor.mapper.ScheduleSlotMapper;
import com.medical.doctor.mapper.ScheduleTemplateMapper;
import com.medical.doctor.service.impl.ScheduleServiceImpl;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceImplTest {

    @Mock
    private ScheduleTemplateMapper scheduleTemplateMapper;
    @Mock
    private ScheduleSlotMapper scheduleSlotMapper;
    @Mock
    private DoctorProfileMapper doctorProfileMapper;
    @Mock
    private DoctorDepartmentMapper doctorDepartmentMapper;

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
        template.setPeriod("morning");
        when(scheduleTemplateMapper.selectById(templateId)).thenReturn(template);

        scheduleService.deleteTemplate(templateId);

        verify(scheduleSlotMapper).delete(any());
        verify(scheduleTemplateMapper).deleteById(templateId);
    }

    private ArgumentMatcher<ScheduleTemplate> periodIs(String expectedPeriod) {
        return template -> template != null && expectedPeriod.equals(template.getPeriod());
    }
}
