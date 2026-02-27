# 05 - 知识库服务 (knowledge-service)

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现医疗知识库管理、文档上传解析、文本分块、Embedding 向量化、向量存储/检索，为 RAG 提供语义检索能力。

**Architecture:** 文档上传后异步解析（PDF/Word/TXT）→ 文本分块（RecursiveCharacterTextSplitter 策略）→ 调用通义千问 Embedding API 生成向量 → 存入 Milvus。检索时接收自然语言查询，转为向量后在 Milvus 中做相似度搜索，返回 Top-K 相关文档片段。

**Tech Stack:** Spring Boot 3.3.x, Spring AI (Embedding), Milvus Java SDK, Apache Tika (文档解析), MyBatis-Plus

**前置依赖:** `01-project-init.md` + `02-common-modules.md` 完成

---

## Task 1: POM 依赖补充

**Files:**
- Modify: `medical-ai/medical-service/medical-knowledge-service/pom.xml`

追加以下依赖：
```xml
<!-- Spring AI - Embedding (通义千问) -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
</dependency>
<!-- Milvus SDK -->
<dependency>
    <groupId>io.milvus</groupId>
    <artifactId>milvus-sdk-java</artifactId>
</dependency>
<!-- Apache Tika - 文档解析 -->
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parsers-standard-package</artifactId>
    <version>2.9.1</version>
</dependency>
<!-- 异步任务 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

---

## Task 2: 数据库表设计

**Files:**
- Create: `medical-ai/medical-service/medical-knowledge-service/src/main/resources/db/V1__init_knowledge_tables.sql`

```sql
-- 知识库表
CREATE TABLE IF NOT EXISTS `knowledge_base` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(128) NOT NULL COMMENT '知识库名称',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '描述',
    `collection_name` VARCHAR(128) NOT NULL COMMENT 'Milvus collection 名称',
    `document_count` INT DEFAULT 0 COMMENT '文档数',
    `chunk_count` INT DEFAULT 0 COMMENT '分块数',
    `status` TINYINT DEFAULT 0 COMMENT '0正常 1构建中 2异常',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `create_by` BIGINT DEFAULT NULL,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `update_by` BIGINT DEFAULT NULL,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';

-- 知识文档表
CREATE TABLE IF NOT EXISTS `knowledge_document` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `kb_id` BIGINT NOT NULL COMMENT '知识库ID',
    `file_name` VARCHAR(256) NOT NULL COMMENT '原始文件名',
    `file_path` VARCHAR(512) NOT NULL COMMENT '存储路径',
    `file_type` VARCHAR(32) NOT NULL COMMENT '文件类型(pdf/docx/txt)',
    `file_size` BIGINT DEFAULT 0 COMMENT '文件大小(bytes)',
    `chunk_count` INT DEFAULT 0 COMMENT '分块数',
    `parse_status` TINYINT DEFAULT 0 COMMENT '0待处理 1处理中 2成功 3失败',
    `error_msg` VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_kb_id` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文档表';

-- 知识分块表 (metadata 存在 MySQL, 向量存在 Milvus)
CREATE TABLE IF NOT EXISTS `knowledge_chunk` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `kb_id` BIGINT NOT NULL COMMENT '知识库ID',
    `doc_id` BIGINT NOT NULL COMMENT '文档ID',
    `chunk_index` INT NOT NULL COMMENT '分块序号',
    `content` TEXT NOT NULL COMMENT '分块文本内容',
    `token_count` INT DEFAULT 0 COMMENT 'token数',
    `milvus_id` VARCHAR(64) DEFAULT NULL COMMENT 'Milvus中的向量ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_doc_id` (`doc_id`),
    KEY `idx_kb_id` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识分块表';
```

---

## Task 3: Entity + Mapper + DTO/VO

按照 user-service / doctor-service 相同的模式创建：
- Entity: `KnowledgeBase`, `KnowledgeDocument`, `KnowledgeChunk`
- Mapper: 各自继承 BaseMapper
- DTO: `KnowledgeBaseDTO(name, description)`, `ChunkManualDTO(kbId, content)`
- VO: `KnowledgeBaseVO`, `KnowledgeDocumentVO`, `KnowledgeChunkVO`, `SearchResultVO(content, score, docName, chunkIndex)`

---

## Task 4: Milvus 客户端配置

**Files:**
- Create: `com/medical/knowledge/config/MilvusConfig.java`

```java
package com.medical.knowledge.config;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.client.ConnectConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MilvusConfig {

    @Value("${milvus.host:localhost}")
    private String host;

    @Value("${milvus.port:19530}")
    private int port;

    @Bean
    public MilvusClientV2 milvusClient() {
        ConnectConfig config = ConnectConfig.builder()
                .uri("http://" + host + ":" + port)
                .build();
        return new MilvusClientV2(config);
    }
}
```

application.yml 追加：
```yaml
milvus:
  host: ${MILVUS_HOST:localhost}
  port: ${MILVUS_PORT:19530}

spring:
  ai:
    openai:
      api-key: ${DASHSCOPE_API_KEY:your-key}
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      embedding:
        model: text-embedding-v3
```

---

## Task 5: Service - 向量存储操作

**Files:**
- Create: `com/medical/knowledge/service/VectorStoreService.java`
- Create: `com/medical/knowledge/service/impl/VectorStoreServiceImpl.java`

功能清单：
- `createCollection(collectionName)` - 创建 Milvus collection（维度 1024 for text-embedding-v3）
- `dropCollection(collectionName)` - 删除 collection
- `insertVectors(collectionName, List<VectorData>)` - 批量插入向量
- `deleteVectors(collectionName, List<String> ids)` - 删除向量
- `search(collectionName, float[] queryVector, int topK)` - 相似度搜索

VectorData 结构：
```java
@Data
public class VectorData {
    private String id;
    private float[] vector;
    private Long chunkId;    // 关联 knowledge_chunk.id
    private Long docId;      // 关联 knowledge_document.id
    private String content;  // 文本内容（用于返回）
}
```

---

## Task 6: Service - 文档解析

**Files:**
- Create: `com/medical/knowledge/service/DocumentParseService.java`
- Create: `com/medical/knowledge/service/impl/DocumentParseServiceImpl.java`

功能清单：
- `parseDocument(filePath, fileType)` → String（提取纯文本）
  - PDF: Apache Tika
  - DOCX: Apache Tika
  - TXT: 直接读取
- `splitText(text, chunkSize, overlap)` → List<String>（文本分块）
  - 默认 chunkSize=500字符, overlap=50字符
  - 按段落分割 → 合并至 chunkSize → 保留 overlap

```java
public List<String> splitText(String text, int chunkSize, int overlap) {
    // 1. 按换行符分段
    // 2. 合并短段落，确保每块接近 chunkSize
    // 3. 相邻块之间保留 overlap 个字符的重叠
    // 4. 返回分块列表
}
```

---

## Task 7: Service - Embedding 服务

**Files:**
- Create: `com/medical/knowledge/service/EmbeddingService.java`
- Create: `com/medical/knowledge/service/impl/EmbeddingServiceImpl.java`

```java
package com.medical.knowledge.service.impl;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    private final EmbeddingModel embeddingModel;

    @Override
    public float[] embed(String text) {
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
        return response.getResult().getOutput();
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        EmbeddingResponse response = embeddingModel.embedForResponse(texts);
        return response.getResults().stream()
                .map(r -> r.getOutput())
                .toList();
    }
}
```

---

## Task 8: Service - 知识库管理 (核心编排)

**Files:**
- Create: `com/medical/knowledge/service/KnowledgeBaseService.java`
- Create: `com/medical/knowledge/service/impl/KnowledgeBaseServiceImpl.java`

功能清单：
- `createKb(dto)` - 创建知识库 + 创建 Milvus collection
- `deleteKb(id)` - 删除知识库 + 删除 Milvus collection + 删除所有文档/分块
- `listKb(pageQuery)` - 分页查询知识库
- `uploadDocument(kbId, MultipartFile)` - 上传文档 → 保存文件 → 创建记录 → 异步触发解析
- `processDocument(docId)` - **核心异步流程**：
  1. 更新状态为处理中
  2. 调用 DocumentParseService 解析文档
  3. 调用 splitText 分块
  4. 批量保存 knowledge_chunk 到 MySQL
  5. 调用 EmbeddingService 批量向量化
  6. 调用 VectorStoreService 批量插入 Milvus
  7. 更新文档/知识库的 chunk_count
  8. 更新状态为成功（或失败+错误信息）
- `deleteDocument(docId)` - 删除文档 + 删除对应分块和向量
- `rebuildIndex(kbId)` - 重建索引（删除旧向量 → 重新 Embedding → 重新插入）
- `search(kbId, query, topK)` - **语义检索**：
  1. 调用 EmbeddingService 将 query 向量化
  2. 调用 VectorStoreService 搜索 Top-K
  3. 根据 chunkId 从 MySQL 获取完整文本和元数据
  4. 返回 SearchResultVO 列表
- `addManualChunk(dto)` - 手动添加知识条目
- `listChunks(docId, pageQuery)` - 查看文档分块列表

---

## Task 9: 异步任务配置

**Files:**
- Create: `com/medical/knowledge/config/AsyncConfig.java`

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean
    public Executor documentProcessExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("doc-process-");
        executor.initialize();
        return executor;
    }
}
```

在 processDocument 方法上标注 `@Async("documentProcessExecutor")`

---

## Task 10: Controller 层

**Files:**
- Create: `com/medical/knowledge/controller/KnowledgeBaseController.java`

```
POST   /kb                    - 创建知识库（ADMIN）
GET    /kb/list                - 知识库列表（ADMIN）
GET    /kb/{id}                - 知识库详情
DELETE /kb/{id}                - 删除知识库（ADMIN）
POST   /kb/{id}/rebuild        - 重建索引（ADMIN）

POST   /kb/{kbId}/document     - 上传文档（multipart）（ADMIN）
GET    /kb/{kbId}/documents     - 文档列表
DELETE /kb/document/{docId}     - 删除文档（ADMIN）
GET    /kb/document/{docId}/chunks - 分块列表

POST   /kb/{kbId}/chunk        - 手动添加知识条目（ADMIN）
DELETE /kb/chunk/{chunkId}      - 删除知识条目（ADMIN）

POST   /kb/search              - 语义检索（内部接口 + 管理端预览）
```

---

## Task 11: Feign API 定义

**Files:**
- Create: `medical-ai/medical-api/medical-knowledge-api/src/main/java/com/medical/api/knowledge/RemoteKnowledgeService.java`
- Create: `medical-ai/medical-api/medical-knowledge-api/src/main/java/com/medical/api/knowledge/dto/KnowledgeSearchRequest.java`
- Create: `medical-ai/medical-api/medical-knowledge-api/src/main/java/com/medical/api/knowledge/dto/KnowledgeSearchResult.java`

```java
@FeignClient(name = "medical-knowledge-service", path = "/kb")
public interface RemoteKnowledgeService {

    @PostMapping("/inner/search")
    R<List<KnowledgeSearchResult>> search(@RequestBody KnowledgeSearchRequest request);
}
```

```java
@Data
public class KnowledgeSearchRequest {
    private Long kbId;       // 指定知识库，null 则搜索全部
    private String query;    // 自然语言查询
    private Integer topK;    // 返回条数，默认5
}

@Data
public class KnowledgeSearchResult {
    private String content;
    private Double score;
    private String documentName;
    private Integer chunkIndex;
}
```

---

## Task 12: 编译验证 + Commit

Run: `mvn clean compile -f medical-ai/pom.xml`

```bash
git add .
git commit -m "feat(knowledge-service): implement KB management, document parsing, embedding, Milvus vector search"
```

---

## 检查清单

- [ ] DDL: knowledge_base, knowledge_document, knowledge_chunk
- [ ] Milvus 客户端配置 + collection CRUD
- [ ] Apache Tika 文档解析（PDF/Word/TXT）
- [ ] 文本分块（chunkSize + overlap）
- [ ] Spring AI Embedding 接入（通义千问 text-embedding-v3）
- [ ] 文档上传 → 异步解析 → 分块 → 向量化 → 存入 Milvus 完整流程
- [ ] 语义检索接口（query → embedding → Milvus 搜索 → 返回 Top-K）
- [ ] 知识库/文档/分块 CRUD
- [ ] 手动添加知识条目
- [ ] 重建索引功能
- [ ] Feign API 暴露搜索接口
- [ ] 编译通过
