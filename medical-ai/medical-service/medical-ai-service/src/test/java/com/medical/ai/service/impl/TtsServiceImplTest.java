package com.medical.ai.service.impl;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TtsServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void synthesize_shouldReturnNullWhenSynthesisTimesOut() {
        FlowRuleManager.loadRules(List.of());
        TimeoutTtsServiceImpl service = new TimeoutTtsServiceImpl();
        configure(service);

        String result = service.synthesize("timeout test");

        assertNull(result);
    }

    @Test
    void synthesize_shouldWriteAudioWhenSynthesisSucceeds() throws Exception {
        FlowRuleManager.loadRules(List.of());
        SuccessTtsServiceImpl service = new SuccessTtsServiceImpl();
        configure(service);

        String result = service.synthesize("ok");

        assertNotNull(result);
        assertTrue(result.startsWith("/ai/chat/tts/"));
        try (Stream<Path> files = Files.list(tempDir)) {
            assertTrue(files.anyMatch(Files::isRegularFile));
        }
    }

    @Test
    void synthesize_shouldReturnNullWhenSentinelBlocks() throws Exception {
        FlowRule rule = new FlowRule(TtsServiceImpl.TTS_RESOURCE);
        rule.setGrade(RuleConstant.FLOW_GRADE_THREAD);
        rule.setCount(1);
        FlowRuleManager.loadRules(List.of(rule));

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> holderFailure = new AtomicReference<>();
        Thread holder = new Thread(() -> {
            try (Entry ignored = SphU.entry(TtsServiceImpl.TTS_RESOURCE)) {
                entered.countDown();
                release.await();
            } catch (Throwable t) {
                holderFailure.set(t);
            }
        });
        holder.start();
        entered.await();

        SuccessTtsServiceImpl service = new SuccessTtsServiceImpl();
        configure(service);

        String result = service.synthesize("blocked");

        release.countDown();
        holder.join();

        assertNull(holderFailure.get());
        assertNull(result);
    }

    @Test
    void transcodeWavToMp3_shouldProduceMp3Bytes() throws Exception {
        TtsServiceImpl service = new TtsServiceImpl();
        configure(service);

        byte[] mp3 = service.transcodeWavToMp3(createTestWavAudio());

        assertNotNull(mp3);
        assertTrue(mp3.length > 0);
    }

    private void configure(TtsServiceImpl service) {
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "baseUrl", "https://api.xiaomimimo.com/v1");
        ReflectionTestUtils.setField(service, "model", "test-model");
        ReflectionTestUtils.setField(service, "voice", "test-voice");
        ReflectionTestUtils.setField(service, "format", "wav");
        ReflectionTestUtils.setField(service, "connectTimeoutMs", 1000);
        ReflectionTestUtils.setField(service, "readTimeoutMs", 1000);
        ReflectionTestUtils.setField(service, "mp3BitRate", 128);
        ReflectionTestUtils.setField(service, "audioPath", tempDir.toString());
    }

    private static class TimeoutTtsServiceImpl extends TtsServiceImpl {
        @Override
        protected byte[] synthesizeWavAudio(String text) throws Exception {
            throw new TimeoutException("mock timeout");
        }
    }

    private static class SuccessTtsServiceImpl extends TtsServiceImpl {
        @Override
        protected byte[] synthesizeWavAudio(String text) {
            return new byte[] {1, 2, 3};
        }

        @Override
        protected byte[] transcodeWavToMp3(byte[] wavAudio) {
            return new byte[] {9, 8, 7};
        }
    }

    private static byte[] createTestWavAudio() throws Exception {
        AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
        byte[] pcm = new byte[3200];
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(pcm);
             AudioInputStream audioInputStream = new AudioInputStream(inputStream, format, pcm.length / format.getFrameSize());
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, outputStream);
            return outputStream.toByteArray();
        }
    }
}
