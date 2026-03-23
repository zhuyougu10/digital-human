package com.medical.ai.controller;

import com.medical.ai.domain.dto.ChatRequestDTO;
import com.medical.ai.domain.dto.CreateSessionDTO;
import com.medical.ai.domain.vo.ChatMessageVO;
import com.medical.ai.domain.vo.ChatSessionVO;
import com.medical.ai.domain.vo.SseMessageVO;
import com.medical.ai.service.ChatService;
import com.medical.common.core.domain.R;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Value("${tts.cosyvoice.audio-path:tts-audio}")
    private String ttsAudioPath;

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
        Long userId = SecurityUtil.getUserId();
        return R.ok(chatService.getSessionMessages(sessionId, userId));
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
                if (e instanceof BusinessException businessException) {
                    errorMsg.setContent(businessException.getMessage());
                } else {
                    errorMsg.setContent("服务暂时不可用，请稍后重试");
                }
                return Flux.just(ServerSentEvent.<SseMessageVO>builder()
                    .event("error")
                    .data(errorMsg)
                    .build());
            });
    }

    @PostMapping("/session/{sessionId}/end")
    public R<Void> endSession(@PathVariable Long sessionId) {
        Long userId = SecurityUtil.getUserId();
        chatService.endSession(sessionId, userId);
        return R.ok();
    }

    @DeleteMapping("/session/{sessionId}")
    public R<Void> deleteSession(@PathVariable Long sessionId) {
        Long userId = SecurityUtil.getUserId();
        chatService.deleteSession(sessionId, userId);
        return R.ok();
    }

    @GetMapping("/tts/{fileName}")
    public ResponseEntity<Resource> getTtsAudio(@PathVariable String fileName) {
        try {
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                return ResponseEntity.badRequest().build();
            }
            Path filePath = Paths.get(ttsAudioPath, fileName);
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new FileSystemResource(filePath.toFile());
            return ResponseEntity.ok()
                .header("Content-Type", "audio/mpeg")
                .header("Cache-Control", "public, max-age=86400")
                .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
