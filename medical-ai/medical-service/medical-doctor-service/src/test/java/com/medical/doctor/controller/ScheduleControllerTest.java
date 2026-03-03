package com.medical.doctor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.doctor.domain.dto.ScheduleTemplateDTO;
import com.medical.doctor.domain.entity.ScheduleTemplate;
import com.medical.doctor.domain.vo.ScheduleSlotVO;
import com.medical.doctor.service.ScheduleService;
import com.medical.common.core.exception.ErrorCode;
import com.medical.common.core.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScheduleController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
public class ScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ScheduleService scheduleService;

    @Test
    void getTemplates_success() throws Exception {
        when(scheduleService.getTemplatesByDoctor(1L)).thenReturn(Collections.singletonList(new ScheduleTemplate()));

        mockMvc.perform(get("/schedule/template/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void createTemplate_success() throws Exception {
        doNothing().when(scheduleService).saveTemplate(eq(1L), any(ScheduleTemplateDTO.class));

        ScheduleTemplateDTO dto = new ScheduleTemplateDTO();
        // set fields

        mockMvc.perform(post("/schedule/template/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    void createTemplate_invalidParam() throws Exception {
        mockMvc.perform(post("/schedule/template/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    void deleteTemplate_success() throws Exception {
        doNothing().when(scheduleService).deleteTemplate(1L);

        mockMvc.perform(delete("/schedule/template/1"))
                .andExpect(status().isOk());
    }

    @Test
    void getSlots_success() throws Exception {
        when(scheduleService.getAvailableSlots(eq(1L), any(LocalDate.class))).thenReturn(Collections.singletonList(new ScheduleSlotVO()));

        mockMvc.perform(get("/schedule/slots")
                .param("doctorId", "1")
                .param("date", "2026-03-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getSlots_missingParam() throws Exception {
        mockMvc.perform(get("/schedule/slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    void getSlotsByDepartment_success() throws Exception {
        when(scheduleService.getAvailableSlotsByDepartment(eq(1L), any(LocalDate.class))).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/schedule/slots/department")
                .param("departmentId", "1")
                .param("date", "2026-03-10"))
                .andExpect(status().isOk());
    }

    @Test
    void generateSlots_success() throws Exception {
        doNothing().when(scheduleService).generateSlots(any(LocalDate.class), any(LocalDate.class));

        mockMvc.perform(post("/schedule/generate")
                .param("startDate", "2026-03-10")
                .param("endDate", "2026-03-17"))
                .andExpect(status().isOk());
    }

    @Test
    void innerGetSlots_success() throws Exception {
        ScheduleSlotVO vo = new ScheduleSlotVO();
        vo.setId(1L);
        when(scheduleService.getAvailableSlots(eq(1L), any(LocalDate.class))).thenReturn(Collections.singletonList(vo));

        mockMvc.perform(get("/schedule/inner/slots")
                .param("doctorId", "1")
                .param("date", "2026-03-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    void innerBookSlot_success() throws Exception {
        when(scheduleService.bookSlot(1L)).thenReturn(true);

        mockMvc.perform(post("/schedule/inner/slots/1/book"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void innerBookSlot_alreadyBooked() throws Exception {
        when(scheduleService.bookSlot(1L)).thenReturn(false);

        mockMvc.perform(post("/schedule/inner/slots/1/book"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    void innerCancelSlot_success() throws Exception {
        when(scheduleService.cancelSlot(1L)).thenReturn(true);

        mockMvc.perform(post("/schedule/inner/slots/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void getSlots_invalidDateFormat() throws Exception {
        mockMvc.perform(get("/schedule/slots")
                .param("doctorId", "1")
                .param("date", "bad-date"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }
}
