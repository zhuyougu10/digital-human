package com.medical.knowledge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.api.knowledge.dto.KnowledgeSearchRequest;
import com.medical.common.core.domain.PageQuery;
import com.medical.common.core.domain.PageResult;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import com.medical.common.core.handler.GlobalExceptionHandler;
import com.medical.knowledge.TestKnowledgeApplication;
import com.medical.knowledge.domain.dto.ChunkManualDTO;
import com.medical.knowledge.domain.dto.KnowledgeBaseDTO;
import com.medical.knowledge.domain.entity.KnowledgeBase;
import com.medical.knowledge.domain.entity.KnowledgeChunk;
import com.medical.knowledge.domain.entity.KnowledgeDocument;
import com.medical.knowledge.domain.vo.KnowledgeBaseVO;
import com.medical.knowledge.domain.vo.SearchResultVO;
import com.medical.knowledge.mapper.KnowledgeBaseMapper;
import com.medical.knowledge.mapper.KnowledgeChunkMapper;
import com.medical.knowledge.mapper.KnowledgeDocumentMapper;
import com.medical.knowledge.service.KnowledgeBaseService;
import com.medical.knowledge.service.VectorStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KnowledgeBaseController.class)
@ContextConfiguration(classes = TestKnowledgeApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
public class KnowledgeBaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KnowledgeBaseService knowledgeBaseService;

    @MockBean
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @MockBean
    private KnowledgeChunkMapper knowledgeChunkMapper;

    @MockBean
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @MockBean
    private VectorStoreService vectorStoreService;

    @Test
    void createKb_success() throws Exception {
        when(knowledgeBaseService.createKb(any(KnowledgeBaseDTO.class))).thenReturn(1L);

        KnowledgeBaseDTO dto = new KnowledgeBaseDTO();
        dto.setName("KB1");

        mockMvc.perform(post("/kb")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void createKb_invalidParam() throws Exception {
        mockMvc.perform(post("/kb")
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    void listKb_success() throws Exception {
        PageResult<KnowledgeBaseVO> pageResult = new PageResult<>();
        pageResult.setRecords(Collections.singletonList(new KnowledgeBaseVO()));
        
        when(knowledgeBaseService.listKb(any(PageQuery.class))).thenReturn(pageResult);

        mockMvc.perform(get("/kb/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void listKb_pagination() throws Exception {
        when(knowledgeBaseService.listKb(any(PageQuery.class))).thenReturn(new PageResult<>());

        mockMvc.perform(get("/kb/list")
                .param("pageNum", "2")
                .param("pageSize", "5"))
                .andExpect(status().isOk());
    }

    @Test
    void getKbById_success() throws Exception {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(1L);
        when(knowledgeBaseService.getKbById(1L)).thenReturn(vo);

        mockMvc.perform(get("/kb/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void getKbById_notFound() throws Exception {
        when(knowledgeBaseService.getKbById(999L)).thenThrow(new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "Not found"));

        mockMvc.perform(get("/kb/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND.getCode()));
    }

    @Test
    void deleteKb_success() throws Exception {
        doNothing().when(knowledgeBaseService).deleteKb(1L);

        mockMvc.perform(delete("/kb/1"))
                .andExpect(status().isOk());
    }

    @Test
    void uploadDocument_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());
        when(knowledgeBaseService.uploadDocument(eq(1L), any())).thenReturn(100L);

        mockMvc.perform(multipart("/kb/1/document").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(100));
    }

    @Test
    void uploadDocument_noFile() throws Exception {
        mockMvc.perform(multipart("/kb/1/document"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    void listDocuments_success() throws Exception {
        Page<KnowledgeDocument> page = new Page<>();
        page.setRecords(Collections.singletonList(new KnowledgeDocument()));
        page.setTotal(1L);
        
        when(knowledgeDocumentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        mockMvc.perform(get("/kb/1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void deleteDocument_success() throws Exception {
        doNothing().when(knowledgeBaseService).deleteDocument(1L);

        mockMvc.perform(delete("/kb/document/1"))
                .andExpect(status().isOk());
    }

    @Test
    void listChunks_success() throws Exception {
        when(knowledgeBaseService.listChunks(eq(1L), any(PageQuery.class))).thenReturn(new PageResult<>());

        mockMvc.perform(get("/kb/document/1/chunks"))
                .andExpect(status().isOk());
    }

    @Test
    void createManualChunk_success() throws Exception {
        when(knowledgeBaseService.addManualChunk(any(ChunkManualDTO.class))).thenReturn(100L);

        ChunkManualDTO dto = new ChunkManualDTO();
        dto.setContent("Test chunk");

        mockMvc.perform(post("/kb/1/chunk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(100));
    }

    @Test
    void createManualChunk_invalidParam() throws Exception {
        mockMvc.perform(post("/kb/1/chunk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    void deleteChunk_success() throws Exception {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(1L);
        chunk.setKbId(1L);
        
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(1L);
        kb.setCollectionName("test_coll");
        kb.setChunkCount(10);
        
        when(knowledgeChunkMapper.selectById(1L)).thenReturn(chunk);
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(kb);
        when(knowledgeBaseMapper.updateById(any(KnowledgeBase.class))).thenReturn(1);
        when(knowledgeChunkMapper.deleteById(1L)).thenReturn(1);
        
        mockMvc.perform(delete("/kb/chunk/1"))
                .andExpect(status().isOk());
        
        verify(vectorStoreService).deleteVectors(eq("test_coll"), anyList());
    }

    @Test
    void search_success() throws Exception {
        when(knowledgeBaseService.search(eq(1L), eq("query"), eq(5))).thenReturn(Collections.singletonList(new SearchResultVO()));

        KnowledgeSearchRequest req = new KnowledgeSearchRequest();
        req.setKbId(1L);
        req.setQuery("query");
        req.setTopK(5);

        mockMvc.perform(post("/kb/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void search_shouldReturnBusyMessageWhenKnowledgeSearchDegrades() throws Exception {
        when(knowledgeBaseService.search(eq(1L), eq("query"), eq(5)))
                .thenThrow(new BusinessException(ErrorCode.AI_RATE_LIMIT, "请求过于频繁，请稍后再试"));

        KnowledgeSearchRequest req = new KnowledgeSearchRequest();
        req.setKbId(1L);
        req.setQuery("query");
        req.setTopK(5);

        mockMvc.perform(post("/kb/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.AI_RATE_LIMIT.getCode()))
                .andExpect(jsonPath("$.msg").value("知识检索暂不可用，请稍后重试"));
    }

    @Test
    void innerSearch_shouldReturnEmptyListWhenKnowledgeSearchDegrades() throws Exception {
        when(knowledgeBaseService.search(eq(1L), eq("query"), eq(5)))
                .thenThrow(new BusinessException(ErrorCode.EMBEDDING_ERROR, "404 - "));

        KnowledgeSearchRequest req = new KnowledgeSearchRequest();
        req.setKbId(1L);
        req.setQuery("query");
        req.setTopK(5);

        mockMvc.perform(post("/kb/inner/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void search_emptyQuery() throws Exception {
        mockMvc.perform(post("/kb/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    void innerSearch_success() throws Exception {
        when(knowledgeBaseService.search(eq(1L), eq("query"), eq(5))).thenReturn(Collections.singletonList(new SearchResultVO()));

        KnowledgeSearchRequest req = new KnowledgeSearchRequest();
        req.setKbId(1L);
        req.setQuery("query");
        req.setTopK(5);

        mockMvc.perform(post("/kb/inner/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
