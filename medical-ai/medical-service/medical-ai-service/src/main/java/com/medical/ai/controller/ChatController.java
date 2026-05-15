package com.medical.ai.controller;

import com.medical.ai.domain.dto.ChatRequestDTO;
import com.medical.ai.domain.dto.CreateSessionDTO;
import com.medical.ai.domain.vo.ChatMessageVO;
import com.medical.ai.domain.vo.ChatSessionVO;
import com.medical.ai.domain.vo.SseMessageVO;
import com.medical.ai.service.ChatService;
import com.medical.ai.service.TtsService;
import com.medical.common.core.domain.R;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.security.util.SecurityUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Validated
public class ChatController {

    private final ChatService chatService;
    private final TtsService ttsService;

    @Value("${tts.audio-path:tts-audio}")
    private String ttsAudioPath;

    @PostMapping("/session")
    public R<ChatSessionVO> createSession(@RequestBody @Valid CreateSessionDTO dto) {
        Long userId = currentUserId();
        return R.ok(chatService.createSession(userId, dto.getSessionType()));
    }

    @GetMapping("/sessions")
    public R<List<ChatSessionVO>> listSessions() {
        Long userId = currentUserId();
        return R.ok(chatService.listSessions(userId));
    }

    @GetMapping("/session/{sessionId}/messages")
    public R<List<ChatMessageVO>> getMessages(@PathVariable Long sessionId) {
        Long userId = currentUserId();
        return R.ok(chatService.getSessionMessages(sessionId, userId));
    }

    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<SseMessageVO>>> chat(@RequestBody @Valid ChatRequestDTO dto) {
        Long userId = currentUserId();
        Flux<ServerSentEvent<SseMessageVO>> body = chatService.chat(dto.getSessionId(), userId, dto.getMessage())
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
        return ResponseEntity.ok()
            .header("X-Accel-Buffering", "no")
            .header("Cache-Control", "no-cache, no-store, must-revalidate")
            .header("Connection", "keep-alive")
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(body);
    }

    @PostMapping("/session/{sessionId}/end")
    public R<Void> endSession(@PathVariable Long sessionId) {
        Long userId = currentUserId();
        chatService.endSession(sessionId, userId);
        return R.ok();
    }

    @DeleteMapping("/session/{sessionId}")
    public R<Void> deleteSession(@PathVariable Long sessionId) {
        Long userId = currentUserId();
        chatService.deleteSession(sessionId, userId);
        return R.ok();
    }

    Long currentUserId() {
        return SecurityUtil.getUserId();
    }

    @GetMapping("/tts/{fileName}")
    public ResponseEntity<Resource> getTtsAudio(@PathVariable("fileName") @NotBlank String fileName) {
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

    @DeleteMapping("/tts/cleanup")
    public R<Integer> cleanupTtsAudio(@RequestBody @NotEmpty List<@NotBlank String> fileNames) {
        return R.ok(ttsService.cleanupGeneratedAudio(fileNames));
    }
}
