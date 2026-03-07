package com.medical.ai.agent.tool;

import com.medical.api.knowledge.RemoteKnowledgeService;
import com.medical.api.knowledge.dto.KnowledgeSearchResult;
import com.medical.common.core.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

@Slf4j
@Component
public class KnowledgeSearchTool {

    private final RemoteKnowledgeService remoteKnowledgeService;
    private final Executor toolCallExecutor;

    public KnowledgeSearchTool(RemoteKnowledgeService remoteKnowledgeService,
                               @Qualifier("toolCallExecutor") Executor toolCallExecutor) {
        this.remoteKnowledgeService = remoteKnowledgeService;
        this.toolCallExecutor = toolCallExecutor;
    }

    @Bean
    @Description("在医学知识库中搜索相关内容。输入query为搜索查询文本。返回相关知识片段列表。")
    public Function<KnowledgeSearchRequest, List<KnowledgeSearchResult>> searchKnowledge() {
        return request -> {
            log.info("Function call: searchKnowledge, query={}", request.getQuery());
            return CompletableFuture.supplyAsync(() -> {
                com.medical.api.knowledge.dto.KnowledgeSearchRequest searchReq =
                        new com.medical.api.knowledge.dto.KnowledgeSearchRequest();
                searchReq.setQuery(request.getQuery());
                searchReq.setTopK(request.getTopK() != null ? request.getTopK() : 5);
                R<List<KnowledgeSearchResult>> result = remoteKnowledgeService.search(searchReq);
                return result.isSuccess() ? result.getData() : List.<KnowledgeSearchResult>of();
            }, toolCallExecutor).join();
        };
    }
}
