package com.medical.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.ai.TestAiApplication;
import com.medical.ai.domain.dto.ChatRequestDTO;
import com.medical.ai.domain.vo.ChatMessageVO;
import com.medical.ai.domain.vo.ChatSessionVO;
import com.medical.ai.domain.vo.SseMessageVO;
import com.medical.ai.service.ChatService;
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
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EncyclopediaController.class)
@ContextConfiguration(classes = TestAiApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
public class EncyclopediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChatService chatService;

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
    void createSession_success() throws Exception {
        ChatSessionVO vo = new ChatSessionVO();
        vo.setId(1L);
        when(chatService.createSession(eq(1L), eq("ENCYCLOPEDIA"))).thenReturn(vo);

        mockMvc.perform(post("/encyclopedia/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void listSessions_success() throws Exception {
        when(chatService.listSessions(1L)).thenReturn(Collections.singletonList(new ChatSessionVO()));

        mockMvc.perform(get("/encyclopedia/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getMessages_success() throws Exception {
        when(chatService.getSessionMessages(1L)).thenReturn(Collections.singletonList(new ChatMessageVO()));

        mockMvc.perform(get("/encyclopedia/session/1/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void chat_sseStream() throws Exception {
        SseMessageVO vo = new SseMessageVO();
        vo.setType("message");
        vo.setContent("Hello");
        
        when(chatService.chat(eq(1L), eq(1L), eq("Hello"))).thenReturn(Flux.just(vo));

        ChatRequestDTO dto = new ChatRequestDTO();
        dto.setSessionId(1L);
        dto.setMessage("Hello");

        MvcResult result = mockMvc.perform(post("/encyclopedia/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(request().asyncStarted())
                .andReturn();
        
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    @Test
    void chat_emptyMessage() throws Exception {
        ChatRequestDTO dto = new ChatRequestDTO();
        dto.setSessionId(1L);
        dto.setMessage("");

        when(chatService.chat(eq(1L), eq(1L), eq(""))).thenReturn(Flux.empty());

        mockMvc.perform(post("/encyclopedia/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }
}
