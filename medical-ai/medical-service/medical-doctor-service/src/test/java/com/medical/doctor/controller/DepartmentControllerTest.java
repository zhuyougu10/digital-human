package com.medical.doctor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import com.medical.common.core.handler.GlobalExceptionHandler;
import com.medical.doctor.domain.dto.DepartmentDTO;
import com.medical.doctor.domain.vo.DepartmentVO;
import com.medical.doctor.service.DepartmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DepartmentController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
public class DepartmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DepartmentService departmentService;

    @Test
    void listDepartments_success() throws Exception {
        when(departmentService.list(null)).thenReturn(Collections.singletonList(new DepartmentVO()));

        mockMvc.perform(get("/department/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void listDepartments_withKeyword() throws Exception {
        when(departmentService.list("test")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/department/list")
                .param("keyword", "test"))
                .andExpect(status().isOk());
        
        verify(departmentService).list("test");
    }

    @Test
    void getDepartmentById_success() throws Exception {
        DepartmentVO vo = new DepartmentVO();
        vo.setId(1L);
        when(departmentService.getById(1L)).thenReturn(vo);

        mockMvc.perform(get("/department/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void getDepartmentById_notFound() throws Exception {
        when(departmentService.getById(999L)).thenThrow(new BusinessException(ErrorCode.FAIL, "Department not found"));

        mockMvc.perform(get("/department/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.FAIL.getCode()));
    }

    @Test
    void createDepartment_success() throws Exception {
        doNothing().when(departmentService).create(any(DepartmentDTO.class));

        DepartmentDTO dto = new DepartmentDTO();
        dto.setName("Internal Medicine");

        mockMvc.perform(post("/department")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    void createDepartment_invalidParam() throws Exception {
        // Missing body
        mockMvc.perform(post("/department")
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    void updateDepartment_success() throws Exception {
        doNothing().when(departmentService).update(eq(1L), any(DepartmentDTO.class));

        DepartmentDTO dto = new DepartmentDTO();
        dto.setName("Updated Name");

        mockMvc.perform(put("/department/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteDepartment_success() throws Exception {
        doNothing().when(departmentService).delete(1L);

        mockMvc.perform(delete("/department/1"))
                .andExpect(status().isOk());
    }

    @Test
    void toggleDepartmentStatus_success() throws Exception {
        doNothing().when(departmentService).toggleStatus(1L);

        mockMvc.perform(put("/department/1/toggle-status"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteDepartment_notFound() throws Exception {
        doThrow(new BusinessException(ErrorCode.FAIL, "Department not found")).when(departmentService).delete(999L);

        mockMvc.perform(delete("/department/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.FAIL.getCode()));
    }
}
