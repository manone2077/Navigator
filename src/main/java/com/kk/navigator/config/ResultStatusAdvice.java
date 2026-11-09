package com.kk.navigator.config;

import com.kk.navigator.entity.R;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 让 HTTP 状态码与响应中的 code 保持一致：业务失败不再返回 200，
 * 便于健康检查、日志和监控识别错误。前端会读取响应中的 msg 显示给用户。
 */
@RestControllerAdvice
public class ResultStatusAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return R.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof R<?> r && r.getCode() >= 400 && r.getCode() < 600) {
            response.setStatusCode(HttpStatusCode.valueOf(r.getCode()));
        }
        return body;
    }
}
