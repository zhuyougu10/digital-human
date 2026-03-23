package com.medical.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medical.api.knowledge.dto.KnowledgeSearchRequest;
import com.medical.api.knowledge.dto.KnowledgeSearchResult;
import com.medical.common.core.domain.PageQuery;
import com.medical.common.core.domain.PageResult;
import com.medical.common.core.domain.R;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import com.medical.knowledge.domain.dto.ChunkManualDTO;
import com.medical.knowledge.domain.dto.KnowledgeBaseDTO;
import com.medical.knowledge.domain.entity.KnowledgeBase;
import com.medical.knowledge.domain.entity.KnowledgeChunk;
import com.medical.knowledge.domain.entity.KnowledgeDocument;
import com.medical.knowledge.domain.vo.KnowledgeChunkVO;
import com.medical.knowledge.domain.vo.KnowledgeDocumentVO;
import com.medical.knowledge.domain.vo.KnowledgeBaseVO;
import com.medical.knowledge.domain.vo.SearchResultVO;
import com.medical.knowledge.service.impl.KnowledgeBaseServiceImpl;
import com.medical.knowledge.mapper.KnowledgeBaseMapper;
import com.medical.knowledge.mapper.KnowledgeChunkMapper;
import com.medical.knowledge.mapper.KnowledgeDocumentMapper;
import com.medical.knowledge.service.KnowledgeBaseService;
import com.medical.knowledge.service.VectorStoreService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/kb")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final VectorStoreService vectorStoreService;

    @SaCheckRole("ADMIN")
    @PostMapping
    public R<Long> createKb(@RequestBody @Valid KnowledgeBaseDTO dto) {
        return R.ok(knowledgeBaseService.createKb(dto));
    }

    @SaCheckRole("ADMIN")
    @GetMapping("/list")
    public R<PageResult<KnowledgeBaseVO>> listKb(PageQuery pageQuery) {
        return R.ok(knowledgeBaseService.listKb(pageQuery));
    }

    @GetMapping("/{id}")
    public R<KnowledgeBaseVO> getKbById(@PathVariable Long id) {
        return R.ok(knowledgeBaseService.getKbById(id));
    }

    @SaCheckRole("ADMIN")
    @DeleteMapping("/{id}")
    public R<Void> deleteKb(@PathVariable Long id) {
        knowledgeBaseService.deleteKb(id);
        return R.ok();
    }

    @SaCheckRole("ADMIN")
    @PostMapping("/{kbId}/document")
    public R<Long> uploadDocument(@PathVariable Long kbId, @RequestParam("file") MultipartFile file) {
        return R.ok(knowledgeBaseService.uploadDocument(kbId, file));
    }

    @GetMapping("/{kbId}/documents")
    public R<PageResult<KnowledgeDocumentVO>> listDocuments(@PathVariable Long kbId, PageQuery pageQuery) {
        int pageNum = pageQuery == null || pageQuery.getPageNum() == null ? 1 : pageQuery.getPageNum();
        int pageSize = pageQuery == null || pageQuery.getPageSize() == null ? 10 : pageQuery.getPageSize();
        Page<KnowledgeDocument> page = knowledgeDocumentMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getKbId, kbId)
                        .orderByDesc(KnowledgeDocument::getCreateTime));
        List<KnowledgeDocumentVO> records = page.getRecords().stream().map(this::toKnowledgeDocumentVO).toList();
        return R.ok(PageResult.of(records, page.getTotal(), (int) page.getCurrent(), (int) page.getSize()));
    }

    @SaCheckRole("ADMIN")
    @DeleteMapping("/document/{docId}")
    public R<Void> deleteDocument(@PathVariable Long docId) {
        knowledgeBaseService.deleteDocument(docId);
        return R.ok();
    }

    @GetMapping("/document/{docId}/chunks")
    public R<PageResult<KnowledgeChunkVO>> listChunks(@PathVariable Long docId, PageQuery pageQuery) {
        return R.ok(knowledgeBaseService.listChunks(docId, pageQuery));
    }

    @GetMapping("/{kbId}/manual-chunks")
    public R<PageResult<KnowledgeChunkVO>> listManualChunks(@PathVariable Long kbId, PageQuery pageQuery) {
        return R.ok(knowledgeBaseService.listManualChunks(kbId, pageQuery));
    }

    @SaCheckRole("ADMIN")
    @PostMapping("/{kbId}/chunk")
    public R<Long> addManualChunk(@PathVariable Long kbId, @RequestBody @Valid ChunkManualDTO dto) {
        dto.setKbId(kbId);
        return R.ok(knowledgeBaseService.addManualChunk(dto));
    }

    @SaCheckRole("ADMIN")
    @DeleteMapping("/chunk/{chunkId}")
    @Transactional
    public R<Void> deleteChunk(@PathVariable Long chunkId) {
        KnowledgeChunk chunk = knowledgeChunkMapper.selectById(chunkId);
        if (chunk == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Chunk not found");
        }
        KnowledgeBase kb = knowledgeBaseMapper.selectById(chunk.getKbId());
        if (kb != null && kb.getCollectionName() != null && !kb.getCollectionName().isBlank()) {
            String vectorId = chunk.getMilvusId() == null ? String.valueOf(chunk.getId()) : chunk.getMilvusId();
            vectorStoreService.deleteVectors(kb.getCollectionName(), List.of(vectorId));
            kb.setChunkCount(Math.max(0, (kb.getChunkCount() == null ? 0 : kb.getChunkCount()) - 1));
            knowledgeBaseMapper.updateById(kb);
        }
        knowledgeChunkMapper.deleteById(chunkId);
        return R.ok();
    }

    @PostMapping("/search")
    public R<List<SearchResultVO>> search(@RequestBody KnowledgeSearchRequest request) {
        try {
            return R.ok(knowledgeBaseService.search(request.getKbId(), request.getQuery(), request.getTopK()));
        } catch (BusinessException e) {
            if (isSearchDegradeException(e)) {
                return R.fail(ErrorCode.AI_RATE_LIMIT.getCode(), KnowledgeBaseServiceImpl.SEARCH_DEGRADE_MESSAGE);
            }
            throw e;
        }
    }

    @PostMapping("/inner/search")
    public R<List<KnowledgeSearchResult>> innerSearch(@RequestBody KnowledgeSearchRequest request) {
        try {
            List<KnowledgeSearchResult> result = knowledgeBaseService.search(
                            request.getKbId(), request.getQuery(), request.getTopK())
                    .stream()
                    .map(this::toKnowledgeSearchResult)
                    .toList();
            return R.ok(result);
        } catch (BusinessException e) {
            if (isSearchDegradeException(e)) {
                return R.ok(Collections.emptyList());
            }
            throw e;
        }
    }

    private boolean isSearchDegradeException(BusinessException e) {
        return e.getCode() == ErrorCode.AI_RATE_LIMIT.getCode()
                || e.getCode() == ErrorCode.EMBEDDING_ERROR.getCode();
    }

    private KnowledgeDocumentVO toKnowledgeDocumentVO(KnowledgeDocument doc) {
        KnowledgeDocumentVO vo = new KnowledgeDocumentVO();
        vo.setId(doc.getId());
        vo.setKbId(doc.getKbId());
        vo.setFileName(doc.getFileName());
        vo.setFilePath(doc.getFilePath());
        vo.setFileType(doc.getFileType());
        vo.setFileSize(doc.getFileSize());
        vo.setChunkCount(doc.getChunkCount());
        vo.setParseStatus(doc.getParseStatus());
        vo.setErrorMsg(doc.getErrorMsg());
        vo.setCreateTime(doc.getCreateTime());
        vo.setUpdateTime(doc.getUpdateTime());
        return vo;
    }

    private KnowledgeSearchResult toKnowledgeSearchResult(SearchResultVO vo) {
        KnowledgeSearchResult result = new KnowledgeSearchResult();
        result.setContent(vo.getContent());
        result.setScore(vo.getScore());
        result.setDocumentName(vo.getDocName());
        result.setChunkIndex(vo.getChunkIndex());
        return result;
    }
}
