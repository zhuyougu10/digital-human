package com.medical.knowledge.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;

class EmbeddingServiceImplTest {

    private EmbeddingModel embeddingModel;
    private EmbeddingServiceImpl embeddingService;

    @BeforeEach
    void setUp() {
        FlowRuleManager.loadRules(List.of());
        embeddingModel = mock(EmbeddingModel.class);
        embeddingService = new EmbeddingServiceImpl(embeddingModel);
    }

    @Test
    void embed_shouldThrowRateLimitWhenSentinelBlocks() throws Exception {
        FlowRule rule = new FlowRule(EmbeddingServiceImpl.EMBED_RESOURCE);
        rule.setGrade(RuleConstant.FLOW_GRADE_THREAD);
        rule.setCount(1);
        FlowRuleManager.loadRules(List.of(rule));

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> holderFailure = new AtomicReference<>();
        Thread holder = new Thread(() -> {
            try {
                Entry entry = SphU.entry(EmbeddingServiceImpl.EMBED_RESOURCE);
                try {
                    entered.countDown();
                    release.await();
                } finally {
                    entry.exit();
                }
            } catch (Throwable t) {
                holderFailure.set(t);
            }
        });
        holder.start();
        entered.await();

        BusinessException ex = assertThrows(BusinessException.class, () -> embeddingService.embed("query"));

        release.countDown();
        holder.join();

        assertEquals(ErrorCode.AI_RATE_LIMIT.getCode(), ex.getCode());
        assertEquals(KnowledgeBaseServiceImpl.SEARCH_DEGRADE_MESSAGE, ex.getMessage());
        assertEquals(null, holderFailure.get());
    }

    @Test
    void embedBatch_shouldWrapEmbeddingErrors() {
        when(embeddingModel.embedForResponse(List.of("a", "b"))).thenThrow(new RuntimeException("embedding down"));

        BusinessException ex = assertThrows(BusinessException.class, () -> embeddingService.embedBatch(List.of("a", "b")));

        assertEquals(ErrorCode.EMBEDDING_ERROR.getCode(), ex.getCode());
        assertEquals("embedding down", ex.getMessage());
    }
}
