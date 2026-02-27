package com.medical.common.core.domain;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class R<T> implements Serializable {

    private int code;
    private String msg;
    private T data;

    public static final int SUCCESS_CODE = 200;
    public static final int FAIL_CODE = 500;

    private R(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> R<T> ok() {
        return new R<>(SUCCESS_CODE, "操作成功", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(SUCCESS_CODE, "操作成功", data);
    }

    public static <T> R<T> ok(String msg, T data) {
        return new R<>(SUCCESS_CODE, msg, data);
    }

    public static <T> R<T> fail() {
        return new R<>(FAIL_CODE, "操作失败", null);
    }

    public static <T> R<T> fail(String msg) {
        return new R<>(FAIL_CODE, msg, null);
    }

    public static <T> R<T> fail(int code, String msg) {
        return new R<>(code, msg, null);
    }

    public boolean isSuccess() {
        return SUCCESS_CODE == this.code;
    }
}
