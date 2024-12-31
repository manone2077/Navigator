package com.kk.navigator.entity;

import lombok.Data;

@Data
public class R<T> {
    private int code;
    private String msg;
    private T data;

    public static <T> R<T> success(String message) {
        R<T> r = new R<>();
        r.setCode(ResultCode.SUCCESS.code);
        r.setMsg(message);
        return r;
    }

    public static <T> R<T> success(String message,T data) {
        R<T> r = new R<>();
        r.setCode(ResultCode.SUCCESS.code);
        r.setData(data);
        r.setMsg(message);
        return r;
    }

    public static <T> R<T> fail(String message) {
        R<T> r = new R<>();
        r.setCode(ResultCode.FAIL.code);
        r.setMsg(message);
        return r;
    }

    public static <T> R<T> fail(String message,T data) {
        R<T> r = new R<>();
        r.setCode(ResultCode.FAIL.code);
        r.setData(data);
        r.setMsg(message);
        return r;
    }

    public static <T> R<T> error(String message) {
        R<T> r = new R<>();
        r.setCode(ResultCode.INTERNAL_SERVER_ERROR.code);
        r.setMsg(message);
        return r;
    }

}
