package com.medical.knowledge.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import com.medical.knowledge.mapper.KnowledgeBaseMapper;
import com.medical.knowledge.mapper.KnowledgeChunkMapper;
import com.medical.knowledge.mapper.KnowledgeDocumentMapper;
import com.medical.knowledge.service.DocumentParseService;
import com.medical.knowledge.service.EmbeddingService;
import com.medical.knowledge.service.VectorStoreService;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KnowledgeBaseServiceImplTest {

    private KnowledgeBaseServiceImpl knowledgeBaseService;

    @BeforeEach
    void setUp() {
        FlowRuleManager.loadRules(List.of());
        knowledgeBaseService = new KnowledgeBaseServiceImpl(
                mock(KnowledgeBaseMapper.class),
                mock(KnowledgeDocumentMapper.class),
                mock(KnowledgeChunkMapper.class),
                mock(VectorStoreService.class),
                mock(DocumentParseService.class),
                mock(EmbeddingService.class));
    }

    @Test
    void search_shouldThrowRateLimitWhenSentinelBlocks() throws Exception {
        FlowRule rule = new FlowRule(KnowledgeBaseServiceImpl.SEARCH_RESOURCE);
        rule.setGrade(RuleConstant.FLOW_GRADE_THREAD);
        rule.setCount(1);
        FlowRuleManager.loadRules(List.of(rule));

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> holderFailure = new AtomicReference<>();
        Thread holder = new Thread(() -> {
            try {
                Entry entry = SphU.entry(KnowledgeBaseServiceImpl.SEARCH_RESOURCE);
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

        BusinessException ex = assertThrows(BusinessException.class,
                () -> knowledgeBaseService.search(1L, "query", 5));

        release.countDown();
        holder.join();

        assertEquals(ErrorCode.AI_RATE_LIMIT.getCode(), ex.getCode());
        assertEquals(KnowledgeBaseServiceImpl.SEARCH_DEGRADE_MESSAGE, ex.getMessage());
        assertEquals(null, holderFailure.get());
    }
}
