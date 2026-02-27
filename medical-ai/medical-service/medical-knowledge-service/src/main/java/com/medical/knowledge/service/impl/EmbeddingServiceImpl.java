package com.medical.knowledge.service.impl;

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

    private final EmbeddingModel embeddingModel;

    @Override
    public float[] embed(String text) {
        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
            return response.getResult().getOutput();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.EMBEDDING_ERROR, e.getMessage());
        }
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(texts);
            return response.getResults().stream()
                    .map(result -> result.getOutput())
                    .toList();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.EMBEDDING_ERROR, e.getMessage());
        }
    }
}
