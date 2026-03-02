package com.medical.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medical.common.core.domain.PageQuery;
import com.medical.common.core.domain.PageResult;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import com.medical.knowledge.domain.VectorData;
import com.medical.knowledge.domain.dto.ChunkManualDTO;
import com.medical.knowledge.domain.dto.KnowledgeBaseDTO;
import com.medical.knowledge.domain.entity.KnowledgeBase;
import com.medical.knowledge.domain.entity.KnowledgeChunk;
import com.medical.knowledge.domain.entity.KnowledgeDocument;
import com.medical.knowledge.domain.vo.KnowledgeBaseVO;
import com.medical.knowledge.domain.vo.KnowledgeChunkVO;
import com.medical.knowledge.domain.vo.SearchResultVO;
import com.medical.knowledge.mapper.KnowledgeBaseMapper;
import com.medical.knowledge.mapper.KnowledgeChunkMapper;
import com.medical.knowledge.mapper.KnowledgeDocumentMapper;
import com.medical.knowledge.service.DocumentParseService;
import com.medical.knowledge.service.EmbeddingService;
import com.medical.knowledge.service.KnowledgeBaseService;
import com.medical.knowledge.service.VectorStoreService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_CHUNK_OVERLAP = 50;

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final VectorStoreService vectorStoreService;
    private final DocumentParseService documentParseService;
    private final EmbeddingService embeddingService;
    @Lazy
    @Autowired
    private KnowledgeBaseService self;

    @Value("${knowledge.upload-path:./uploads}")
    private String uploadPath;

    @Override
    @Transactional
    public Long createKb(KnowledgeBaseDTO dto) {
        KnowledgeBase duplicate = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getName, dto.getName())
                .last("limit 1"));
        if (duplicate != null) {
            throw new BusinessException(ErrorCode.FAIL, "Knowledge base name already exists");
        }

        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(dto.getName());
        kb.setDescription(dto.getDescription());
        kb.setCollectionName("kb_tmp_" + System.currentTimeMillis());
        kb.setDocumentCount(0);
        kb.setChunkCount(0);
        kb.setStatus(0);
        knowledgeBaseMapper.insert(kb);

        String collectionName = "kb_" + kb.getId();
        kb.setCollectionName(collectionName);
        knowledgeBaseMapper.updateById(kb);
        vectorStoreService.createCollection(collectionName);
        return kb.getId();
    }

    @Override
    @Transactional
    public void deleteKb(Long id) {
        KnowledgeBase kb = getKbEntity(id);
        vectorStoreService.dropCollection(kb.getCollectionName());
        knowledgeChunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getKbId, id));
        knowledgeDocumentMapper.delete(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKbId, id));
        knowledgeBaseMapper.deleteById(id);
    }

    @Override
    public PageResult<KnowledgeBaseVO> listKb(PageQuery pageQuery) {
        int pageNum = pageQuery == null || pageQuery.getPageNum() == null ? 1 : pageQuery.getPageNum();
        int pageSize = pageQuery == null || pageQuery.getPageSize() == null ? 10 : pageQuery.getPageSize();
        Page<KnowledgeBase> page = knowledgeBaseMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<KnowledgeBase>().orderByDesc(KnowledgeBase::getCreateTime));
        List<KnowledgeBaseVO> records = page.getRecords().stream()
                .map(this::toKnowledgeBaseVO)
                .toList();
        return PageResult.of(records, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public KnowledgeBaseVO getKbById(Long id) {
        return toKnowledgeBaseVO(getKbEntity(id));
    }

    @Override
    @Transactional
    public Long uploadDocument(Long kbId, MultipartFile file) {
        KnowledgeBase kb = getKbEntity(kbId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "File is empty");
        }

        String originalName = file.getOriginalFilename();
        String fileType = getFileType(originalName);
        String storedName = UUID.randomUUID() + (fileType.isEmpty() ? "" : "." + fileType);
        Path dir = Path.of(uploadPath, "kb-" + kbId);
        Path target = dir.resolve(storedName);

        try {
            Files.createDirectories(dir);
            file.transferTo(target.toFile());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.FAIL, "Save file failed: " + e.getMessage());
        }

        KnowledgeDocument document = new KnowledgeDocument();
        document.setKbId(kbId);
        document.setFileName(originalName == null || originalName.isBlank() ? storedName : originalName);
        document.setFilePath(target.toString());
        document.setFileType(fileType);
        document.setFileSize(file.getSize());
        document.setChunkCount(0);
        document.setParseStatus(0);
        knowledgeDocumentMapper.insert(document);

        kb.setDocumentCount(nvl(kb.getDocumentCount()) + 1);
        knowledgeBaseMapper.updateById(kb);

        self.processDocument(document.getId());
        return document.getId();
    }

    @Override
    @Async("documentProcessExecutor")
    @Transactional
    public void processDocument(Long docId) {
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(docId);
        if (document == null) {
            return;
        }
        KnowledgeBase kb = knowledgeBaseMapper.selectById(document.getKbId());
        if (kb == null) {
            markDocFailed(document, "Knowledge base not found");
            return;
        }

        document.setParseStatus(1);
        document.setErrorMsg(null);
        knowledgeDocumentMapper.updateById(document);

        try {
            String text = documentParseService.parseDocument(document.getFilePath(), document.getFileType());
            List<String> chunks = documentParseService.splitText(text, DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);

            if (chunks.isEmpty()) {
                document.setChunkCount(0);
                document.setParseStatus(2);
                knowledgeDocumentMapper.updateById(document);
                return;
            }

            List<KnowledgeChunk> savedChunks = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setKbId(document.getKbId());
                chunk.setDocId(document.getId());
                chunk.setChunkIndex(i);
                chunk.setContent(chunks.get(i));
                chunk.setTokenCount(chunks.get(i).length());
                knowledgeChunkMapper.insert(chunk);
                savedChunks.add(chunk);
            }

            List<float[]> vectors = embeddingService.embedBatch(chunks);
            if (vectors.size() != savedChunks.size()) {
                throw new BusinessException(ErrorCode.EMBEDDING_ERROR, "Embedding size mismatch");
            }

            List<VectorData> vectorDataList = new ArrayList<>(savedChunks.size());
            for (int i = 0; i < savedChunks.size(); i++) {
                KnowledgeChunk chunk = savedChunks.get(i);
                VectorData data = new VectorData();
                data.setId(String.valueOf(chunk.getId()));
                data.setVector(vectors.get(i));
                data.setChunkId(chunk.getId());
                data.setDocId(chunk.getDocId());
                data.setContent(chunk.getContent());
                vectorDataList.add(data);
            }
            vectorStoreService.insertVectors(kb.getCollectionName(), vectorDataList);

            for (KnowledgeChunk chunk : savedChunks) {
                chunk.setMilvusId(String.valueOf(chunk.getId()));
                knowledgeChunkMapper.updateById(chunk);
            }

            int newChunkCount = savedChunks.size();
            document.setChunkCount(newChunkCount);
            document.setParseStatus(2);
            document.setErrorMsg(null);
            knowledgeDocumentMapper.updateById(document);

            kb.setChunkCount(nvl(kb.getChunkCount()) + newChunkCount);
            knowledgeBaseMapper.updateById(kb);
        } catch (Exception e) {
            log.error("Process document failed, docId={}", docId, e);
            markDocFailed(document, e.getMessage());
        }
    }

    @Override
    @Transactional
    public void deleteDocument(Long docId) {
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(docId);
        if (document == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Document not found");
        }

        KnowledgeBase kb = knowledgeBaseMapper.selectById(document.getKbId());
        List<KnowledgeChunk> chunks = knowledgeChunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocId, docId));

        if (kb != null && kb.getCollectionName() != null && !kb.getCollectionName().isBlank() && !chunks.isEmpty()) {
            List<String> vectorIds = chunks.stream()
                    .map(chunk -> chunk.getMilvusId() == null ? String.valueOf(chunk.getId()) : chunk.getMilvusId())
                    .collect(Collectors.toList());
            vectorStoreService.deleteVectors(kb.getCollectionName(), vectorIds);
        }

        knowledgeChunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocId, docId));
        knowledgeDocumentMapper.deleteById(docId);

        if (kb != null) {
            kb.setDocumentCount(Math.max(0, nvl(kb.getDocumentCount()) - 1));
            kb.setChunkCount(Math.max(0, nvl(kb.getChunkCount()) - chunks.size()));
            knowledgeBaseMapper.updateById(kb);
        }
    }

    @Override
    public List<SearchResultVO> search(Long kbId, String query, Integer topK) {
        KnowledgeBase kb = getKbEntity(kbId);
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        float[] queryVector = embeddingService.embed(query);
        int k = topK == null || topK <= 0 ? 5 : topK;
        List<VectorData> vectorResults = vectorStoreService.search(kb.getCollectionName(), queryVector, k);
        if (vectorResults.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> chunkIds = vectorResults.stream()
                .map(this::resolveChunkId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, KnowledgeChunk> chunkMap = chunkIds.isEmpty()
                ? Collections.emptyMap()
                : knowledgeChunkMapper.selectBatchIds(chunkIds).stream()
                .collect(Collectors.toMap(KnowledgeChunk::getId, chunk -> chunk));

        List<Long> docIds = chunkMap.values().stream()
                .map(KnowledgeChunk::getDocId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, KnowledgeDocument> docMap = docIds.isEmpty()
                ? Collections.emptyMap()
                : knowledgeDocumentMapper.selectBatchIds(docIds).stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, doc -> doc));

        List<SearchResultVO> results = new ArrayList<>(vectorResults.size());
        for (VectorData vectorData : vectorResults) {
            Long chunkId = resolveChunkId(vectorData);
            KnowledgeChunk chunk = chunkId == null ? null : chunkMap.get(chunkId);
            KnowledgeDocument doc = chunk == null ? null : docMap.get(chunk.getDocId());

            SearchResultVO vo = new SearchResultVO();
            vo.setScore(vectorData.getScore());
            vo.setContent(chunk != null ? chunk.getContent() : vectorData.getContent());
            vo.setChunkIndex(chunk == null ? null : chunk.getChunkIndex());
            vo.setDocName(doc == null ? null : doc.getFileName());
            results.add(vo);
        }
        return results;
    }

    @Override
    @Transactional
    public Long addManualChunk(ChunkManualDTO dto) {
        KnowledgeBase kb = getKbEntity(dto.getKbId());
        if (dto.getContent() == null || dto.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "content is empty");
        }

        Long manualCount = knowledgeChunkMapper.selectCount(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getKbId, dto.getKbId())
                .eq(KnowledgeChunk::getDocId, 0L));

        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setKbId(dto.getKbId());
        chunk.setDocId(0L);
        chunk.setChunkIndex(manualCount == null ? 0 : manualCount.intValue());
        String chunkContent = StringUtils.hasText(dto.getTitle())
                ? dto.getTitle().trim() + "\n" + dto.getContent()
                : dto.getContent();
        chunk.setContent(chunkContent);
        chunk.setTokenCount(chunkContent.length());
        knowledgeChunkMapper.insert(chunk);

        float[] vector = embeddingService.embed(chunkContent);
        VectorData vectorData = new VectorData();
        vectorData.setId(String.valueOf(chunk.getId()));
        vectorData.setVector(vector);
        vectorData.setChunkId(chunk.getId());
        vectorData.setDocId(chunk.getDocId());
        vectorData.setContent(chunk.getContent());
        vectorStoreService.insertVectors(kb.getCollectionName(), List.of(vectorData));

        chunk.setMilvusId(String.valueOf(chunk.getId()));
        knowledgeChunkMapper.updateById(chunk);

        kb.setChunkCount(nvl(kb.getChunkCount()) + 1);
        knowledgeBaseMapper.updateById(kb);
        return chunk.getId();
    }

    @Override
    public PageResult<KnowledgeChunkVO> listChunks(Long docId, PageQuery pageQuery) {
        int pageNum = pageQuery == null || pageQuery.getPageNum() == null ? 1 : pageQuery.getPageNum();
        int pageSize = pageQuery == null || pageQuery.getPageSize() == null ? 10 : pageQuery.getPageSize();
        Page<KnowledgeChunk> page = knowledgeChunkMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<KnowledgeChunk>()
                        .eq(KnowledgeChunk::getDocId, docId)
                        .orderByAsc(KnowledgeChunk::getChunkIndex));
        List<KnowledgeChunkVO> records = page.getRecords().stream().map(this::toKnowledgeChunkVO).toList();
        return PageResult.of(records, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    private KnowledgeBase getKbEntity(Long kbId) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        return kb;
    }

    private KnowledgeBaseVO toKnowledgeBaseVO(KnowledgeBase kb) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(kb.getId());
        vo.setName(kb.getName());
        vo.setDescription(kb.getDescription());
        vo.setCollectionName(kb.getCollectionName());
        vo.setDocumentCount(kb.getDocumentCount());
        vo.setChunkCount(kb.getChunkCount());
        vo.setStatus(kb.getStatus());
        vo.setCreateTime(kb.getCreateTime());
        vo.setUpdateTime(kb.getUpdateTime());
        return vo;
    }

    private KnowledgeChunkVO toKnowledgeChunkVO(KnowledgeChunk chunk) {
        KnowledgeChunkVO vo = new KnowledgeChunkVO();
        vo.setId(chunk.getId());
        vo.setKbId(chunk.getKbId());
        vo.setDocId(chunk.getDocId());
        vo.setChunkIndex(chunk.getChunkIndex());
        vo.setContent(chunk.getContent());
        vo.setTokenCount(chunk.getTokenCount());
        vo.setMilvusId(chunk.getMilvusId());
        vo.setCreateTime(chunk.getCreateTime());
        return vo;
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private void markDocFailed(KnowledgeDocument document, String message) {
        document.setParseStatus(3);
        if (message != null && message.length() > 1000) {
            document.setErrorMsg(message.substring(0, 1000));
        } else {
            document.setErrorMsg(message);
        }
        knowledgeDocumentMapper.updateById(document);
    }

    private String getFileType(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(idx + 1).toLowerCase();
    }

    private Long resolveChunkId(VectorData vectorData) {
        if (vectorData.getChunkId() != null) {
            return vectorData.getChunkId();
        }
        if (vectorData.getId() == null) {
            return null;
        }
        try {
            return Long.parseLong(vectorData.getId());
        } catch (Exception ignored) {
            return null;
        }
    }
}
