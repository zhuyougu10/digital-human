package com.medical.ai.service.impl;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.utils.Constants;
import com.medical.ai.service.TtsService;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TtsServiceImpl implements TtsService {

    public static final String TTS_RESOURCE = "svc:ai:tts";

    @Value("${tts.cosyvoice.api-key:}")
    private String apiKey;

    @Value("${tts.cosyvoice.model:cosyvoice-v3-flash}")
    private String model;

    @Value("${tts.cosyvoice.voice:longanyang}")
    private String voice;

    @Value("${tts.cosyvoice.volume:50}")
    private int volume;

    @Value("${tts.cosyvoice.speech-rate:1.0}")
    private float speechRate;

    @Value("${tts.cosyvoice.audio-path:tts-audio}")
    private String audioPath;

    @PostConstruct
    public void init() {
        Constants.baseWebsocketApiUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";
        try {
            Files.createDirectories(Paths.get(audioPath));
        } catch (IOException e) {
            log.warn("无法创建 TTS 音频目录: {}", audioPath, e);
        }
    }

    @Override
    public String synthesize(String text) {
        final Entry sentinelEntry;
        try {
            sentinelEntry = SphU.entry(TTS_RESOURCE);
        } catch (BlockException e) {
            log.warn("TTS 熔断/限流触发: {}", e.getRule());
            return null;
        }

        if (text == null || text.isBlank()) {
            sentinelEntry.exit();
            return null;
        }

        if (apiKey == null || apiKey.isBlank() || "your-dashscope-key".equals(apiKey)) {
            log.warn("TTS: DASHSCOPE_API_KEY 未配置，跳过语音合成");
            sentinelEntry.exit();
            return null;
        }

        String processedText = text.length() > 300 ? text.substring(0, 300) : text;
        processedText = stripMarkdown(processedText);
        if (processedText.isBlank()) {
            sentinelEntry.exit();
            return null;
        }

        try {
            log.info("TTS: 开始合成，文本长度={}, 模型={}, 音色={}", processedText.length(), model, voice);

            SpeechSynthesisParam param = SpeechSynthesisParam.builder()
                .apiKey(apiKey)
                .model(model)
                .voice(voice)
                .volume(volume)
                .speechRate(speechRate)
                .build();

            ByteBuffer audio = synthesizeAudio(processedText, param);
            if (audio == null || audio.remaining() == 0) {
                log.warn("TTS: 合成返回空音频");
                return null;
            }

            String fileName = System.currentTimeMillis() + ".mp3";
            Path filePath = Paths.get(audioPath, fileName);
            ByteBuffer audioBuffer = audio.asReadOnlyBuffer();
            byte[] audioBytes = new byte[audioBuffer.remaining()];
            audioBuffer.get(audioBytes);
            Files.write(filePath, audioBytes);

            log.info("TTS: 合成成功，文件={}, 大小={}KB", fileName, audioBytes.length / 1024);
            return "/ai/chat/tts/" + fileName;
        } catch (TimeoutException e) {
            Tracer.trace(e);
            log.error("TTS 合成超时: {}", e.getMessage(), e);
            return null;
        } catch (Exception e) {
            Tracer.trace(e);
            log.error("TTS 合成失败: {}", e.getMessage(), e);
            return null;
        } finally {
            sentinelEntry.exit();
        }
    }

    protected ByteBuffer synthesizeAudio(String text, SpeechSynthesisParam param) throws Exception {
        SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, null);
        try {
            return CompletableFuture.supplyAsync(() -> doSynthesize(synthesizer, text))
                .get(getTimeoutSeconds(), TimeUnit.SECONDS);
        } finally {
            closeSynthesizer(synthesizer);
        }
    }

    protected ByteBuffer doSynthesize(SpeechSynthesizer synthesizer, String text) {
        return synthesizer.call(text);
    }

    protected int getTimeoutSeconds() {
        return 30;
    }

    protected void closeSynthesizer(SpeechSynthesizer synthesizer) {
        try {
            synthesizer.getDuplexApi().close(1000, "bye");
        } catch (Exception e) {
            log.debug("关闭 TTS WebSocket: {}", e.getMessage());
        }
    }

    /**
     * 剥离 Markdown 符号，避免 TTS 朗读格式字符
     */
    private String stripMarkdown(String text) {
        return text
            .replaceAll("```[\\s\\S]*?```", "")
            .replaceAll("`[^`]+`", "")
            .replaceAll("#{1,6}\\s*", "")
            .replaceAll("\\*{1,3}([^*]+)\\*{1,3}", "$1")
            .replaceAll("_{1,3}([^_]+)_{1,3}", "$1")
            .replaceAll("!?\\[([^\\]]*)]\\([^)]*\\)", "$1")
            .replaceAll("[>|\\-]{2,}", "")
            .replaceAll("(?m)^[\\s]*[-*+]\\s+", "")
            .replaceAll("(?m)^[\\s]*\\d+\\.\\s+", "")
            .trim();
    }
}
