package com.medical.ai.service.impl;

import com.medical.ai.service.TtsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TtsServiceImpl implements TtsService {

    @Value("${aliyun.tts.access-key-id:}")
    private String accessKeyId;

    @Value("${aliyun.tts.access-key-secret:}")
    private String accessKeySecret;

    @Value("${aliyun.tts.app-key:}")
    private String appKey;

    @Value("${aliyun.tts.voice:xiaoyun}")
    private String voice;

    @Value("${aliyun.tts.format:mp3}")
    private String format;

    @Value("${aliyun.tts.sample-rate:16000}")
    private int sampleRate;

    @Value("${aliyun.tts.volume:50}")
    private int volume;

    @Override
    public String synthesize(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String processedText = text.length() > 300 ? text.substring(0, 300) : text;

        try {
            log.info("TTS synthesize called for text length={}, voice={}", processedText.length(), voice);
            return null;
        } catch (Exception e) {
            log.warn("TTS synthesis failed: {}", e.getMessage());
            return null;
        }
    }
}

