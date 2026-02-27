package com.medical.api.knowledge;

import com.medical.api.knowledge.dto.KnowledgeSearchRequest;
import com.medical.api.knowledge.dto.KnowledgeSearchResult;
import com.medical.common.core.domain.R;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "medical-knowledge-service", path = "/kb")
public interface RemoteKnowledgeService {

    @PostMapping("/inner/search")
    R<List<KnowledgeSearchResult>> search(@RequestBody KnowledgeSearchRequest request);
}
