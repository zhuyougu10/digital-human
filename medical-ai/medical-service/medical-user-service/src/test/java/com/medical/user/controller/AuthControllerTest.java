package com.medical.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import com.medical.common.core.handler.GlobalExceptionHandler;
import com.medical.user.domain.dto.LoginDTO;
import com.medical.user.domain.dto.RegisterDTO;
import com.medical.user.domain.dto.WxLoginDTO;
import com.medical.user.domain.vo.LoginVO;
import com.medical.user.domain.vo.UserVO;
import com.medical.user.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void login_success() throws Exception {
        LoginVO vo = new LoginVO();
        vo.setToken("test-token");
        UserVO userVO = new UserVO();
        userVO.setId(1L);
        userVO.setUsername("admin");
        vo.setUser(userVO);
        
        when(authService.login(any(LoginDTO.class))).thenReturn(vo);

        LoginDTO dto = new LoginDTO();
        dto.setUsername("admin");
        dto.setPassword("123456");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("test-token"));
    }

    @Test
    void login_invalidParam() throws Exception {
        LoginDTO dto = new LoginDTO();
        // missing username and password

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk()) // GlobalExceptionHandler catches validation error and returns 200
                .andExpect(jsonPath("$.code").value(400)); // PARAM_ERROR
    }

    @Test
    void login_wrongPassword() throws Exception {
        when(authService.login(any(LoginDTO.class))).thenThrow(new BusinessException(ErrorCode.FAIL, "Wrong password"));

        LoginDTO dto = new LoginDTO();
        dto.setUsername("admin");
        dto.setPassword("wrong");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("Wrong password"));
    }

    @Test
    void register_success() throws Exception {
        doNothing().when(authService).register(any(RegisterDTO.class));

        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("newuser");
        dto.setPassword("123456");
        // dto.setRoleKey("PATIENT");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void register_duplicateUsername() throws Exception {
        doThrow(new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "User exists")).when(authService).register(any(RegisterDTO.class));

        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("existing");
        dto.setPassword("123456");
        // dto.setRoleKey("PATIENT");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_ALREADY_EXISTS.getCode()));
    }

    @Test
    void wxLogin_success() throws Exception {
        LoginVO vo = new LoginVO();
        vo.setToken("wx-token");
        when(authService.wxLogin(any(WxLoginDTO.class))).thenReturn(vo);

        WxLoginDTO dto = new WxLoginDTO();
        dto.setCode("wx-code");

        mockMvc.perform(post("/auth/wx-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("wx-token"));
    }

    @Test
    void logout_success() throws Exception {
        doNothing().when(authService).logout();

        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
    
    @Test
    void logout_noToken() throws Exception {
         // Same as success because filters are disabled
         mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk());
    }
}
