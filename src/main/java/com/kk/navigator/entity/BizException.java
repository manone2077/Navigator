package com.kk.navigator.entity;

/**
 * 业务异常：由全局异常处理转换为 {@link R#fail} 响应，并返回对应的 HTTP 状态码
 */
public class BizException extends RuntimeException {
    private final int status;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static BizException notFound() {
        return new BizException(404, "无法找到站点信息");
    }
}
