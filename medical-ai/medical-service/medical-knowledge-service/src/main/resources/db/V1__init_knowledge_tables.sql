-- knowledge base table
CREATE TABLE IF NOT EXISTS `knowledge_base` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(128) NOT NULL COMMENT 'Knowledge base name',
    `description` VARCHAR(500) DEFAULT NULL COMMENT 'Description',
    `collection_name` VARCHAR(128) NOT NULL COMMENT 'Milvus collection name',
    `document_count` INT DEFAULT 0 COMMENT 'Document count',
    `chunk_count` INT DEFAULT 0 COMMENT 'Chunk count',
    `status` TINYINT DEFAULT 0 COMMENT '0-normal 1-building 2-error',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `create_by` BIGINT DEFAULT NULL,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `update_by` BIGINT DEFAULT NULL,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Knowledge base';

-- knowledge document table
CREATE TABLE IF NOT EXISTS `knowledge_document` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `kb_id` BIGINT NOT NULL COMMENT 'Knowledge base ID',
    `file_name` VARCHAR(256) NOT NULL COMMENT 'Original file name',
    `file_path` VARCHAR(512) NOT NULL COMMENT 'Storage path',
    `file_type` VARCHAR(32) NOT NULL COMMENT 'File type(pdf/docx/txt)',
    `file_size` BIGINT DEFAULT 0 COMMENT 'File size(bytes)',
    `chunk_count` INT DEFAULT 0 COMMENT 'Chunk count',
    `parse_status` TINYINT DEFAULT 0 COMMENT '0-pending 1-processing 2-success 3-failed',
    `error_msg` VARCHAR(1000) DEFAULT NULL COMMENT 'Error message',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_kb_id` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Knowledge document';

-- knowledge chunk table (metadata in MySQL, vector in Milvus)
CREATE TABLE IF NOT EXISTS `knowledge_chunk` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `kb_id` BIGINT NOT NULL COMMENT 'Knowledge base ID',
    `doc_id` BIGINT NOT NULL COMMENT 'Document ID',
    `chunk_index` INT NOT NULL COMMENT 'Chunk sequence index',
    `content` TEXT NOT NULL COMMENT 'Chunk text content',
    `token_count` INT DEFAULT 0 COMMENT 'Token count',
    `milvus_id` VARCHAR(64) DEFAULT NULL COMMENT 'Vector ID in Milvus',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_doc_id` (`doc_id`),
    KEY `idx_kb_id` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Knowledge chunk';
