package com.medical.ai.service.impl;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
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

    private void configure(TtsServiceImpl service) {
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "model", "test-model");
        ReflectionTestUtils.setField(service, "voice", "test-voice");
        ReflectionTestUtils.setField(service, "volume", 50);
        ReflectionTestUtils.setField(service, "speechRate", 1.0f);
        ReflectionTestUtils.setField(service, "audioPath", tempDir.toString());
    }

    private static class TimeoutTtsServiceImpl extends TtsServiceImpl {
        @Override
        protected ByteBuffer synthesizeAudio(String text, SpeechSynthesisParam param) throws Exception {
            throw new TimeoutException("mock timeout");
        }
    }

    private static class SuccessTtsServiceImpl extends TtsServiceImpl {
        @Override
        protected ByteBuffer synthesizeAudio(String text, SpeechSynthesisParam param) {
            return ByteBuffer.wrap(new byte[] {1, 2, 3});
        }
    }
}
