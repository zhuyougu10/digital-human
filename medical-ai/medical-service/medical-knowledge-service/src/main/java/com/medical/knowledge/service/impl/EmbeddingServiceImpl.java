package com.medical.knowledge.service.impl;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import com.medical.knowledge.service.EmbeddingService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    public static final String EMBED_RESOURCE = "svc:knowledge:embed";

    private final EmbeddingModel embeddingModel;

    @Override
    public float[] embed(String text) {
        final Entry sentinelEntry;
        try {
            sentinelEntry = SphU.entry(EMBED_RESOURCE);
        } catch (BlockException e) {
            throw new BusinessException(ErrorCode.AI_RATE_LIMIT, KnowledgeBaseServiceImpl.SEARCH_DEGRADE_MESSAGE);
        }

        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
            return response.getResult().getOutput();
        } catch (Exception e) {
            Tracer.trace(e);
            throw new BusinessException(ErrorCode.EMBEDDING_ERROR, e.getMessage());
        } finally {
            sentinelEntry.exit();
        }
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        final Entry sentinelEntry;
        try {
            sentinelEntry = SphU.entry(EMBED_RESOURCE);
        } catch (BlockException e) {
            throw new BusinessException(ErrorCode.AI_RATE_LIMIT, KnowledgeBaseServiceImpl.SEARCH_DEGRADE_MESSAGE);
        }

        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(texts);
            return response.getResults().stream()
                    .map(result -> result.getOutput())
                    .toList();
        } catch (Exception e) {
            Tracer.trace(e);
            throw new BusinessException(ErrorCode.EMBEDDING_ERROR, e.getMessage());
        } finally {
            sentinelEntry.exit();
        }
    }
}
