package com.kk.navigator.entity;

public enum ResultCode {
    SUCCESS(200),
    FAIL(400),
    INTERNAL_SERVER_ERROR(500);

    public final int code;
    ResultCode(int code) {
        this.code = code;
    }
}
