package com.medical.doctor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.common.core.domain.PageQuery;
import com.medical.common.core.domain.PageResult;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import com.medical.common.core.handler.GlobalExceptionHandler;
import com.medical.common.security.util.SecurityUtil;
import com.medical.doctor.domain.dto.DoctorProfileDTO;
import com.medical.doctor.domain.vo.DoctorVO;
import com.medical.doctor.service.DoctorProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DoctorController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
public class DoctorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DoctorProfileService doctorProfileService;

    private MockedStatic<SecurityUtil> securityUtilMock;

    @BeforeEach
    void setUp() {
        securityUtilMock = mockStatic(SecurityUtil.class);
        securityUtilMock.when(SecurityUtil::getUserId).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        securityUtilMock.close();
    }

    @Test
    void listDoctors_success() throws Exception {
        PageResult<DoctorVO> pageResult = new PageResult<>();
        pageResult.setRecords(Collections.singletonList(new DoctorVO()));
        pageResult.setTotal(1L);

        when(doctorProfileService.listByDepartment(any(), any(), any(PageQuery.class))).thenReturn(pageResult);

        mockMvc.perform(get("/doctor/list")
                .param("pageNum", "1")
                .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void listDoctors_withDepartmentFilter() throws Exception {
        when(doctorProfileService.listByDepartment(eq(1L), any(), any(PageQuery.class))).thenReturn(new PageResult<>());

        mockMvc.perform(get("/doctor/list")
                .param("departmentId", "1"))
                .andExpect(status().isOk());
        
        verify(doctorProfileService).listByDepartment(eq(1L), any(), any(PageQuery.class));
    }

    @Test
    void listDoctors_withKeyword() throws Exception {
        when(doctorProfileService.listByDepartment(any(), eq("test"), any(PageQuery.class))).thenReturn(new PageResult<>());

        mockMvc.perform(get("/doctor/list")
                .param("keyword", "test"))
                .andExpect(status().isOk());
        
        verify(doctorProfileService).listByDepartment(any(), eq("test"), any(PageQuery.class));
    }

    @Test
    void getDoctorById_success() throws Exception {
        DoctorVO vo = new DoctorVO();
        vo.setId(1L);
        when(doctorProfileService.getById(1L)).thenReturn(vo);

        mockMvc.perform(get("/doctor/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void getDoctorById_notFound() throws Exception {
        when(doctorProfileService.getById(999L)).thenThrow(new BusinessException(ErrorCode.DOCTOR_NOT_FOUND, "Doctor not found"));

        mockMvc.perform(get("/doctor/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.DOCTOR_NOT_FOUND.getCode()));
    }

    @Test
    void searchDoctors_success() throws Exception {
        when(doctorProfileService.searchBySymptom("headache")).thenReturn(Collections.singletonList(new DoctorVO()));

        mockMvc.perform(get("/doctor/search")
                .param("keywords", "headache"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getMyProfile_success() throws Exception {
        DoctorVO vo = new DoctorVO();
        vo.setId(1L);
        when(doctorProfileService.getByUserId(1L)).thenReturn(vo);

        mockMvc.perform(get("/doctor/my-profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void updateMyProfile_success() throws Exception {
        doNothing().when(doctorProfileService).updateMyProfile(eq(1L), any(DoctorProfileDTO.class));

        DoctorProfileDTO dto = new DoctorProfileDTO();
        dto.setName("Dr. Test");

        mockMvc.perform(put("/doctor/my-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    void createDoctor_success() throws Exception {
        doNothing().when(doctorProfileService).create(any(DoctorProfileDTO.class));

        DoctorProfileDTO dto = new DoctorProfileDTO();
        dto.setUserId(101L);
        dto.setName("Dr. New");

        mockMvc.perform(post("/doctor")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(doctorProfileService).create(argThat(arg ->
                arg != null && arg.getUserId() != null && arg.getUserId().equals(101L)));
    }

    @Test
    void createDoctor_invalidParam() throws Exception {
        mockMvc.perform(post("/doctor")
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    void updateDoctor_success() throws Exception {
        doNothing().when(doctorProfileService).update(eq(1L), any(DoctorProfileDTO.class));

        DoctorProfileDTO dto = new DoctorProfileDTO();
        dto.setName("Dr. Update");

        mockMvc.perform(put("/doctor/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    void innerGetDoctor_success() throws Exception {
        DoctorVO vo = new DoctorVO();
        vo.setId(1L);
        vo.setName("Dr. Inner");
        vo.setUserId(100L);
        
        when(doctorProfileService.getById(1L)).thenReturn(vo);

        mockMvc.perform(get("/doctor/inner/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Dr. Inner"));
    }

    @Test
    void innerGetDoctor_notFound() throws Exception {
        when(doctorProfileService.getById(999L)).thenThrow(new BusinessException(ErrorCode.DOCTOR_NOT_FOUND, "Doctor not found"));

        mockMvc.perform(get("/doctor/inner/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.DOCTOR_NOT_FOUND.getCode()));
    }

    @Test
    void innerSearchBySymptom_success() throws Exception {
        DoctorVO vo = new DoctorVO();
        vo.setId(1L);
        vo.setName("Dr. Inner Search");
        
        when(doctorProfileService.searchBySymptom("fever")).thenReturn(Collections.singletonList(vo));

        mockMvc.perform(get("/doctor/inner/search")
                .param("keywords", "fever"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("Dr. Inner Search"));
    }
}
