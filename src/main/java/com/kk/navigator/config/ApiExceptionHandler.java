package com.kk.navigator.config;

import com.kk.navigator.entity.BizException;
import com.kk.navigator.entity.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 统一的接口错误处理：返回 {code, msg} 格式的中文提示，HTTP 状态码与 code 一致
 * （由 {@link ResultStatusAdvice} 设置）
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> biz(BizException e) {
        return R.of(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public R<Void> tooLarge(MaxUploadSizeExceededException e) {
        return R.of(400, "图片不能超过 5MB");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class, MultipartException.class})
    public R<Void> badRequest(Exception e) {
        return R.of(400, "请求格式不正确");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public R<Void> mediaType(HttpMediaTypeNotSupportedException e) {
        return R.of(415, "不支持的请求格式");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> method(HttpRequestMethodNotSupportedException e) {
        return R.of(405, "不支持的请求方法：" + e.getMethod());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> notFound(NoResourceFoundException e) {
        return R.of(404, "接口不存在");
    }

    @ExceptionHandler(Exception.class)
    public R<Void> unexpected(Exception e) {
        log.error("unexpected error", e);
        return R.of(500, "服务器内部错误，请查看日志");
    }
}
