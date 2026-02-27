package com.medical.ai.agent.tool;

import com.medical.api.knowledge.RemoteKnowledgeService;
import com.medical.api.knowledge.dto.KnowledgeSearchResult;
import com.medical.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeSearchTool {

    private final RemoteKnowledgeService remoteKnowledgeService;

    @Bean
    @Description("在医学知识库中搜索相关内容。输入query为搜索查询文本。返回相关知识片段列表。")
    public Function<KnowledgeSearchRequest, List<KnowledgeSearchResult>> searchKnowledge() {
        return request -> {
            log.info("Function call: searchKnowledge, query={}", request.getQuery());
            com.medical.api.knowledge.dto.KnowledgeSearchRequest searchReq = new com.medical.api.knowledge.dto.KnowledgeSearchRequest();
            searchReq.setQuery(request.getQuery());
            searchReq.setTopK(request.getTopK() != null ? request.getTopK() : 5);
            R<List<KnowledgeSearchResult>> result = remoteKnowledgeService.search(searchReq);
            return result.isSuccess() ? result.getData() : List.of();
        };
    }
}
