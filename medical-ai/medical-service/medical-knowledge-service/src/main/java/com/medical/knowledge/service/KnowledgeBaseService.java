package com.medical.knowledge.service;

import com.medical.common.core.domain.PageQuery;
import com.medical.common.core.domain.PageResult;
import com.medical.knowledge.domain.dto.ChunkManualDTO;
import com.medical.knowledge.domain.dto.KnowledgeBaseDTO;
import com.medical.knowledge.domain.vo.KnowledgeBaseVO;
import com.medical.knowledge.domain.vo.KnowledgeChunkVO;
import com.medical.knowledge.domain.vo.SearchResultVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface KnowledgeBaseService {

    Long createKb(KnowledgeBaseDTO dto);

    void deleteKb(Long id);

    PageResult<KnowledgeBaseVO> listKb(PageQuery pageQuery);

    KnowledgeBaseVO getKbById(Long id);

    Long uploadDocument(Long kbId, MultipartFile file);

    void processDocument(Long docId);

    void deleteDocument(Long docId);

    List<SearchResultVO> search(Long kbId, String query, Integer topK);

    Long addManualChunk(ChunkManualDTO dto);

    PageResult<KnowledgeChunkVO> listChunks(Long docId, PageQuery pageQuery);
}
