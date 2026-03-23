package com.medical.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.ai.TestAiApplication;
import com.medical.ai.domain.vo.ConversationSummaryVO;
import com.medical.ai.service.SummaryService;
import com.medical.common.core.handler.GlobalExceptionHandler;
import com.medical.common.security.util.SecurityUtil;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SummaryController.class)
@ContextConfiguration(classes = TestAiApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
public class SummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SummaryService summaryService;

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
    void getBySession_success() throws Exception {
        ConversationSummaryVO vo = new ConversationSummaryVO();
        vo.setId(1L);
        when(summaryService.getSummaryBySession(1L, 1L)).thenReturn(vo);

        mockMvc.perform(get("/summary/session/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void getBySession_notFound() throws Exception {
        when(summaryService.getSummaryBySession(999L, 1L)).thenReturn(null);

        mockMvc.perform(get("/summary/session/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void getByAppointment_success() throws Exception {
        ConversationSummaryVO vo = new ConversationSummaryVO();
        vo.setId(1L);
        when(summaryService.getSummaryByAppointment(1L)).thenReturn(vo);

        mockMvc.perform(get("/summary/appointment/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void getByAppointment_notFound() throws Exception {
        when(summaryService.getSummaryByAppointment(999L)).thenReturn(null);

        mockMvc.perform(get("/summary/appointment/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
