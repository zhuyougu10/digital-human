package com.medical.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.knowledge.domain.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {
}
