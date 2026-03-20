package com.medical.ai.controller;

import com.medical.ai.domain.dto.ChatRequestDTO;
import com.medical.ai.domain.dto.CreateSessionDTO;
import com.medical.ai.domain.vo.ChatMessageVO;
import com.medical.ai.domain.vo.ChatSessionVO;
import com.medical.ai.domain.vo.SseMessageVO;
import com.medical.ai.service.ChatService;
import com.medical.common.core.domain.R;
import com.medical.common.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/encyclopedia")
@RequiredArgsConstructor
public class EncyclopediaController {

    private final ChatService chatService;

    @PostMapping("/session")
    public R<ChatSessionVO> createSession() {
        Long userId = SecurityUtil.getUserId();
        return R.ok(chatService.createSession(userId, "ENCYCLOPEDIA"));
    }

    @GetMapping("/sessions")
    public R<List<ChatSessionVO>> listSessions() {
        Long userId = SecurityUtil.getUserId();
        // 只返回百科类型的会话可以在 service 层过滤，这里暂用通用接口
        return R.ok(chatService.listSessions(userId));
    }

    @GetMapping("/session/{sessionId}/messages")
    public R<List<ChatMessageVO>> getMessages(@PathVariable Long sessionId) {
        Long userId = SecurityUtil.getUserId();
        return R.ok(chatService.getSessionMessages(sessionId, userId));
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<SseMessageVO>> chat(@RequestBody ChatRequestDTO dto) {
        Long userId = SecurityUtil.getUserId();
        return chatService.chat(dto.getSessionId(), userId, dto.getMessage())
            .map(msg -> ServerSentEvent.<SseMessageVO>builder()
                .event(msg.getType())
                .data(msg)
                .build())
            .onErrorResume(e -> {
                SseMessageVO errorMsg = new SseMessageVO();
                errorMsg.setType("error");
                errorMsg.setContent("服务暂时不可用，请稍后重试");
                return Flux.just(ServerSentEvent.<SseMessageVO>builder()
                    .event("error")
                    .data(errorMsg)
                    .build());
            });
    }
}
