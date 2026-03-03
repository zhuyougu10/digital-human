package com.medical.appointment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.dev33.satoken.stp.StpUtil;
import com.medical.appointment.domain.dto.AppointmentQueryDTO;
import com.medical.appointment.domain.dto.CreateAppointmentDTO;
import com.medical.appointment.domain.vo.AppointmentListVO;
import com.medical.appointment.domain.vo.AppointmentVO;
import com.medical.appointment.service.AppointmentService;
import com.medical.common.core.domain.PageQuery;
import com.medical.common.core.domain.PageResult;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import com.medical.common.core.handler.GlobalExceptionHandler;
import com.medical.appointment.TestAppointmentApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppointmentController.class)
@ContextConfiguration(classes = TestAppointmentApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
public class AppointmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AppointmentService appointmentService;

    private MockedStatic<StpUtil> stpUtilMock;

    @BeforeEach
    void setUp() {
        stpUtilMock = mockStatic(StpUtil.class);
        stpUtilMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        stpUtilMock.close();
    }

    @Test
    void createAppointment_success() throws Exception {
        when(appointmentService.createAppointment(any(CreateAppointmentDTO.class))).thenReturn(100L);

        CreateAppointmentDTO dto = new CreateAppointmentDTO();
        dto.setDoctorId(1L);
        dto.setSlotId(1L);

        mockMvc.perform(post("/appointment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(100));
    }

    @Test
    void createAppointment_invalidParam() throws Exception {
        mockMvc.perform(post("/appointment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    void getMyAppointments_success() throws Exception {
        PageResult<AppointmentListVO> pageResult = new PageResult<>();
        pageResult.setRecords(Collections.singletonList(new AppointmentListVO()));
        pageResult.setTotal(1L);

        when(appointmentService.getMyAppointments(eq(1L), any(PageQuery.class))).thenReturn(pageResult);

        mockMvc.perform(get("/appointment/my")
                .param("pageNum", "1")
                .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void getMyAppointments_pagination() throws Exception {
        when(appointmentService.getMyAppointments(eq(1L), any(PageQuery.class))).thenReturn(new PageResult<>());

        mockMvc.perform(get("/appointment/my")
                .param("pageNum", "2")
                .param("pageSize", "5"))
                .andExpect(status().isOk());
    }

    @Test
    void getDoctorAppointments_success() throws Exception {
        when(appointmentService.getDoctorAppointments(eq(1L), any())).thenReturn(Collections.singletonList(new AppointmentVO()));

        mockMvc.perform(get("/appointment/doctor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getDoctorAppointments_withDate() throws Exception {
        when(appointmentService.getDoctorAppointments(eq(1L), any(LocalDate.class))).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/appointment/doctor")
                .param("date", "2026-03-10"))
                .andExpect(status().isOk());
    }

    @Test
    void getAppointmentById_success() throws Exception {
        AppointmentVO vo = new AppointmentVO();
        vo.setId(1L);
        when(appointmentService.getAppointmentDetail(1L)).thenReturn(vo);

        mockMvc.perform(get("/appointment/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void getAppointmentById_notFound() throws Exception {
        when(appointmentService.getAppointmentDetail(999L)).thenThrow(new BusinessException(ErrorCode.FAIL, "Not found"));

        mockMvc.perform(get("/appointment/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.FAIL.getCode()));
    }

    @Test
    void cancelAppointment_success() throws Exception {
        doNothing().when(appointmentService).cancelAppointment(1L, 1L);

        mockMvc.perform(put("/appointment/1/cancel"))
                .andExpect(status().isOk());
    }

    @Test
    void cancelAppointment_notFound() throws Exception {
        doThrow(new BusinessException(ErrorCode.FAIL, "Not found")).when(appointmentService).cancelAppointment(999L, 1L);

        mockMvc.perform(put("/appointment/999/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.FAIL.getCode()));
    }

    @Test
    void listAppointments_admin_success() throws Exception {
        PageResult<AppointmentListVO> pageResult = new PageResult<>();
        pageResult.setRecords(Collections.emptyList());
        
        when(appointmentService.listAll(any(AppointmentQueryDTO.class), any(PageQuery.class))).thenReturn(pageResult);

        mockMvc.perform(get("/appointment/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void getStatistics_success() throws Exception {
        when(appointmentService.getStatistics(any(), any())).thenReturn(Map.of("total", 10));

        mockMvc.perform(get("/appointment/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(10));
    }

    @Test
    void getStatistics_withDateRange() throws Exception {
        when(appointmentService.getStatistics(any(LocalDate.class), any(LocalDate.class))).thenReturn(Collections.emptyMap());

        mockMvc.perform(get("/appointment/statistics")
                .param("startDate", "2026-03-01")
                .param("endDate", "2026-03-31"))
                .andExpect(status().isOk());
    }

    @Test
    void innerCreate_success() throws Exception {
        when(appointmentService.createAppointment(any(CreateAppointmentDTO.class))).thenReturn(100L);

        mockMvc.perform(post("/appointment/inner/create")
                .param("patientId", "1")
                .param("doctorId", "2")
                .param("slotId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(100));
    }
}
