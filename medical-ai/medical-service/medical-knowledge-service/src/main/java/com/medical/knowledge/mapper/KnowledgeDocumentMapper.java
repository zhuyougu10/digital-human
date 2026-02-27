package com.medical.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.knowledge.domain.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {
}
