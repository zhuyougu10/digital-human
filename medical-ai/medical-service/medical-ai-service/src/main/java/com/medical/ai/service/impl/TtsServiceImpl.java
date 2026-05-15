package com.medical.ai.service.impl;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.ai.service.TtsService;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;

@Slf4j
@Service
public class TtsServiceImpl implements TtsService {

    public static final String TTS_RESOURCE = "svc:ai:tts";
    private static final Pattern GENERATED_AUDIO_FILE_NAME = Pattern.compile("^\\d+\\.mp3$");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${tts.mimo.api-key:}")
    private String apiKey;

    @Value("${tts.mimo.base-url:https://api.xiaomimimo.com/v1}")
    private String baseUrl;

    @Value("${tts.mimo.model:mimo-v2-tts}")
    private String model;

    @Value("${tts.mimo.voice:mimo_default}")
    private String voice;

    @Value("${tts.mimo.format:wav}")
    private String format;

    @Value("${tts.mimo.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${tts.mimo.read-timeout-ms:30000}")
    private int readTimeoutMs;

    @Value("${tts.mimo.mp3-bitrate:128}")
    private int mp3BitRate;

    @Value("${tts.audio-path:tts-audio}")
    private String audioPath;

    private HttpClient httpClient;

    @PostConstruct
    public void init() {
        httpClient = buildHttpClient();
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

        if (apiKey == null || apiKey.isBlank() || "your-mimo-key".equals(apiKey)) {
            log.warn("TTS: MIMO_API_KEY 未配置，跳过语音合成");
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
            log.info("TTS: 开始 MiMo 合成，文本长度={}, 模型={}, 音色={}, 格式={}", processedText.length(), model, voice, format);

            byte[] wavAudio = synthesizeWavAudio(processedText);
            if (wavAudio == null || wavAudio.length == 0) {
                log.warn("TTS: 合成返回空音频");
                return null;
            }

            byte[] mp3Audio = transcodeWavToMp3(wavAudio);
            if (mp3Audio.length == 0) {
                log.warn("TTS: WAV 转 MP3 后为空音频");
                return null;
            }

            String fileName = System.currentTimeMillis() + ".mp3";
            Path filePath = Paths.get(audioPath, fileName);
            Files.write(filePath, mp3Audio);

            log.info("TTS: 合成成功，文件={}, 大小={}KB", fileName, mp3Audio.length / 1024);
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

    @Override
    public int cleanupGeneratedAudio(List<String> fileNames) {
        if (fileNames == null || fileNames.isEmpty()) {
            return 0;
        }

        Path baseDir = Paths.get(audioPath).toAbsolutePath().normalize();
        int deleted = 0;
        for (String fileName : fileNames) {
            validateGeneratedFileName(fileName);
            Path filePath = baseDir.resolve(fileName).normalize();
            if (!filePath.startsWith(baseDir)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "非法TTS文件名");
            }
            try {
                if (Files.deleteIfExists(filePath)) {
                    deleted++;
                }
            } catch (IOException e) {
                log.error("TTS 清理失败: {}", fileName, e);
                throw new BusinessException(ErrorCode.FAIL, "TTS音频清理失败");
            }
        }
        return deleted;
    }

    protected byte[] synthesizeWavAudio(String text) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(normalizeBaseUrl(baseUrl) + "/chat/completions"))
            .timeout(Duration.ofMillis(readTimeoutMs))
            .header("Content-Type", "application/json")
            .header("api-key", apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(text)))
            .build();

        HttpResponse<String> response;
        try {
            response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new TimeoutException("MiMo TTS request timed out");
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("MiMo TTS 请求失败，status=" + response.statusCode() + ", body=" + response.body());
        }

        return decodeAudioContent(response.body());
    }

    protected HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = buildHttpClient();
        }
        return httpClient;
    }

    protected byte[] transcodeWavToMp3(byte[] wavAudio) throws Exception {
        Path wavFile = Files.createTempFile("mimo-tts-", ".wav");
        Path mp3File = Files.createTempFile("mimo-tts-", ".mp3");
        try {
            Files.write(wavFile, wavAudio);

            AudioAttributes audioAttributes = new AudioAttributes();
            audioAttributes.setCodec("libmp3lame");
            audioAttributes.setBitRate(mp3BitRate * 1000);

            applySourceAudioMetadata(wavAudio, audioAttributes);

            EncodingAttributes encodingAttributes = new EncodingAttributes();
            encodingAttributes.setOutputFormat("mp3");
            encodingAttributes.setAudioAttributes(audioAttributes);

            Encoder encoder = new Encoder();
            encoder.encode(new MultimediaObject(wavFile.toFile()), mp3File.toFile(), encodingAttributes);
            return Files.readAllBytes(mp3File);
        } finally {
            deleteTempFileIfExists(wavFile);
            deleteTempFileIfExists(mp3File);
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

    private void validateGeneratedFileName(String fileName) {
        if (fileName == null || fileName.isBlank() || !GENERATED_AUDIO_FILE_NAME.matcher(fileName).matches()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "非法TTS文件名");
        }
    }

    private HttpClient buildHttpClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(connectTimeoutMs))
            .build();
    }

    private String buildRequestBody(String text) throws Exception {
        var requestBody = OBJECT_MAPPER.createObjectNode();
        requestBody.put("model", model);
        requestBody.set("messages", OBJECT_MAPPER.createArrayNode()
            .add(OBJECT_MAPPER.createObjectNode()
                .put("role", "assistant")
                .put("content", text)));
        requestBody.set("audio", OBJECT_MAPPER.createObjectNode()
            .put("format", format)
            .put("voice", voice));
        return OBJECT_MAPPER.writeValueAsString(requestBody);
    }

    private byte[] decodeAudioContent(String responseBody) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(responseBody);
        JsonNode audioDataNode = root.path("choices").path(0).path("message").path("audio").path("data");
        if (!audioDataNode.isTextual() || audioDataNode.asText().isBlank()) {
            throw new IOException("MiMo TTS 响应缺少音频数据: " + responseBody);
        }
        return java.util.Base64.getDecoder().decode(audioDataNode.asText());
    }

    private String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return "https://api.xiaomimimo.com/v1";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private void applySourceAudioMetadata(byte[] wavAudio, AudioAttributes audioAttributes) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(wavAudio);
             AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(inputStream)) {
            AudioFormat sourceFormat = audioInputStream.getFormat();
            int channels = sourceFormat.getChannels();
            if (channels > 0) {
                audioAttributes.setChannels(channels);
            }
            int sampleRate = Math.round(sourceFormat.getSampleRate());
            if (sampleRate > 0) {
                audioAttributes.setSamplingRate(sampleRate);
            }
        } catch (Exception e) {
            log.warn("TTS: 读取 WAV 元数据失败，使用 JAVE 默认转码参数: {}", e.getMessage());
        }
    }

    private void deleteTempFileIfExists(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("TTS: 删除临时文件失败: {}", file, e);
        }
    }
}
