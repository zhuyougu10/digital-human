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
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/session")
    public R<ChatSessionVO> createSession(@RequestBody CreateSessionDTO dto) {
        Long userId = SecurityUtil.getUserId();
        return R.ok(chatService.createSession(userId, dto.getSessionType()));
    }

    @GetMapping("/sessions")
    public R<List<ChatSessionVO>> listSessions() {
        Long userId = SecurityUtil.getUserId();
        return R.ok(chatService.listSessions(userId));
    }

    @GetMapping("/session/{sessionId}/messages")
    public R<List<ChatMessageVO>> getMessages(@PathVariable Long sessionId) {
        return R.ok(chatService.getSessionMessages(sessionId));
    }

    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
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

    @PostMapping("/session/{sessionId}/end")
    public R<Void> endSession(@PathVariable Long sessionId) {
        chatService.endSession(sessionId);
        return R.ok();
    }

    @DeleteMapping("/session/{sessionId}")
    public R<Void> deleteSession(@PathVariable Long sessionId) {
        chatService.deleteSession(sessionId);
        return R.ok();
    }
}