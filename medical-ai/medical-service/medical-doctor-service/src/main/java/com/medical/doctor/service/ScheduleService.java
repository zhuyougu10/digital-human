package com.medical.doctor.service;

import com.medical.doctor.domain.dto.ScheduleTemplateDTO;
import com.medical.doctor.domain.entity.ScheduleTemplate;
import com.medical.doctor.domain.vo.ScheduleSlotVO;
import java.time.LocalDate;
import java.util.List;

public interface ScheduleService {
    List<ScheduleTemplate> getTemplatesByDoctor(Long doctorId);

    void saveTemplate(Long doctorId, ScheduleTemplateDTO dto);

    void deleteTemplate(Long templateId);

    void generateSlots(LocalDate startDate, LocalDate endDate);

    List<ScheduleSlotVO> getAvailableSlots(Long doctorId, LocalDate date);

    List<ScheduleSlotVO> getAvailableSlotsByDepartment(Long departmentId, LocalDate date);

    boolean bookSlot(Long slotId);

    boolean cancelSlot(Long slotId);
}
