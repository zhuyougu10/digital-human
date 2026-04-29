package com.medical.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.api.user.dto.UserInfoDTO;
import com.medical.common.core.domain.PageQuery;
import com.medical.common.core.domain.PageResult;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import com.medical.common.core.handler.GlobalExceptionHandler;
import com.medical.common.security.util.SecurityUtil;
import com.medical.user.domain.dto.UserCreateDTO;
import com.medical.user.domain.dto.UserUpdateDTO;
import com.medical.user.domain.vo.UserVO;
import com.medical.user.service.SysUserService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SysUserController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
public class SysUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SysUserService sysUserService;

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
    void listUsers_success() throws Exception {
        PageResult<UserVO> pageResult = new PageResult<>();
        pageResult.setRecords(Collections.singletonList(new UserVO()));
        pageResult.setTotal(1L);

        when(sysUserService.listUsers(any(PageQuery.class), any())).thenReturn(pageResult);

        mockMvc.perform(get("/user/list")
                .param("pageNum", "1")
                .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void listUsers_withKeyword() throws Exception {
        when(sysUserService.listUsers(any(PageQuery.class), eq("test"))).thenReturn(new PageResult<>());

        mockMvc.perform(get("/user/list")
                .param("keyword", "test"))
                .andExpect(status().isOk());
        
        verify(sysUserService).listUsers(any(PageQuery.class), eq("test"));
    }

    @Test
    void addUser_success() throws Exception {
        UserVO created = new UserVO();
        created.setId(100L);
        created.setUsername("doctor_a");
        when(sysUserService.createUser(any(UserCreateDTO.class))).thenReturn(created);

        UserCreateDTO dto = new UserCreateDTO();
        dto.setUsername("doctor_a");
        dto.setPassword("123456");
        dto.setNickname("张医生");
        dto.setRoleKey("DOCTOR");

        mockMvc.perform(post("/user/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100L))
                .andExpect(jsonPath("$.data.username").value("doctor_a"));
    }

    @Test
    void getUserInfo_success() throws Exception {
        UserVO vo = new UserVO();
        vo.setId(1L);
        when(sysUserService.getUserById(1L)).thenReturn(vo);

        mockMvc.perform(get("/user/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void getUserById_success() throws Exception {
        UserVO vo = new UserVO();
        vo.setId(2L);
        vo.setUsername("doctor_a");
        when(sysUserService.getUserById(2L)).thenReturn(vo);

        mockMvc.perform(get("/user/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.username").value("doctor_a"));
    }

    @Test
    void updateUserInfo_success() throws Exception {
        doNothing().when(sysUserService).updateUser(eq(1L), any(UserUpdateDTO.class));

        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setNickname("newNick");

        mockMvc.perform(put("/user/info")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    void updateUserInfo_invalidParam() throws Exception {
        // Empty body
        mockMvc.perform(put("/user/info")
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    void toggleUserStatus_success() throws Exception {
        doNothing().when(sysUserService).toggleUserStatus(1L);

        mockMvc.perform(put("/user/1/toggle-status"))
                .andExpect(status().isOk());
    }

    @Test
    void toggleUserStatus_notFound() throws Exception {
        doThrow(new BusinessException(ErrorCode.USER_NOT_FOUND, "User not found")).when(sysUserService).toggleUserStatus(999L);

        mockMvc.perform(put("/user/999/toggle-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()));
    }

    @Test
    void assignRole_success() throws Exception {
        doNothing().when(sysUserService).assignRole(1L, "DOCTOR");

        mockMvc.perform(post("/user/1/role/DOCTOR"))
                .andExpect(status().isOk());
    }

    @Test
    void removeRole_success() throws Exception {
        doNothing().when(sysUserService).removeRole(1L, "DOCTOR");

        mockMvc.perform(delete("/user/1/role/DOCTOR"))
                .andExpect(status().isOk());
    }

    @Test
    void innerGetUser_success() throws Exception {
        UserVO vo = new UserVO();
        vo.setId(1L);
        vo.setUsername("admin");
        vo.setNickname("Admin");
        
        when(sysUserService.getUserById(1L)).thenReturn(vo);

        mockMvc.perform(get("/user/inner/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    void innerGetUser_notFound() throws Exception {
        when(sysUserService.getUserById(999L)).thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND, "User not found"));

        mockMvc.perform(get("/user/inner/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()));
    }

    @Test
    void listUsers_pagination() throws Exception {
        PageResult<UserVO> pageResult = new PageResult<>();
        pageResult.setRecords(Collections.emptyList());
        
        when(sysUserService.listUsers(any(PageQuery.class), any())).thenReturn(pageResult);

        mockMvc.perform(get("/user/list")
                .param("pageNum", "2")
                .param("pageSize", "5"))
                .andExpect(status().isOk());
    }
}
