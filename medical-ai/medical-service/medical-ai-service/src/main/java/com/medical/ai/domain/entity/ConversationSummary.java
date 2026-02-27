package com.medical.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.medical.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("conversation_summary")
public class ConversationSummary extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private Long userId;
    private Long appointmentId;
    private String chiefComplaint;
    private String symptoms;
    private String duration;
    private String severity;
    private String medicalHistory;
    private String aiAssessment;
    private String fullSummary;
}
