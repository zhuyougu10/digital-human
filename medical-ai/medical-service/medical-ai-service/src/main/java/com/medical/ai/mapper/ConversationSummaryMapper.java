package com.medical.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.ai.domain.entity.ConversationSummary;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationSummaryMapper extends BaseMapper<ConversationSummary> {
}
