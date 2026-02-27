package com.medical.common.core.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 通用
    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),

    // 用户模块 1xxx
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "用户已存在"),
    USER_PASSWORD_ERROR(1003, "密码错误"),
    USER_DISABLED(1004, "账户已禁用"),
    WX_LOGIN_FAIL(1005, "微信登录失败"),

    // 医生模块 2xxx
    DOCTOR_NOT_FOUND(2001, "医生不存在"),
    DEPARTMENT_NOT_FOUND(2002, "科室不存在"),
    SCHEDULE_CONFLICT(2003, "排班冲突"),

    // AI 模块 3xxx
    AI_SERVICE_ERROR(3001, "AI服务异常"),
    AI_RATE_LIMIT(3002, "请求过于频繁，请稍后再试"),
    TTS_ERROR(3003, "语音合成失败"),

    // 预约模块 4xxx
    SLOT_NOT_AVAILABLE(4001, "号源不可用"),
    APPOINTMENT_NOT_FOUND(4002, "预约不存在"),
    APPOINTMENT_ALREADY_EXISTS(4003, "重复预约"),
    APPOINTMENT_CANCEL_FAIL(4004, "取消预约失败"),

    // 知识库模块 5xxx
    KNOWLEDGE_BASE_NOT_FOUND(5001, "知识库不存在"),
    DOCUMENT_PARSE_ERROR(5002, "文档解析失败"),
    EMBEDDING_ERROR(5003, "向量化处理失败");

    private final int code;
    private final String msg;
}
