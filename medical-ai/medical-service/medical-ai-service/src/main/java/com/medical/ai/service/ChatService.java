package com.medical.ai.service;

import com.medical.ai.domain.vo.ChatMessageVO;
import com.medical.ai.domain.vo.ChatSessionVO;
import com.medical.ai.domain.vo.SseMessageVO;
import reactor.core.publisher.Flux;

import java.util.List;

public interface ChatService {
    ChatSessionVO createSession(Long userId, String sessionType);

    List<ChatSessionVO> listSessions(Long userId);

    List<ChatMessageVO> getSessionMessages(Long sessionId);

    Flux<SseMessageVO> chat(Long sessionId, Long userId, String message);

    void endSession(Long sessionId);

    void deleteSession(Long sessionId);
}

