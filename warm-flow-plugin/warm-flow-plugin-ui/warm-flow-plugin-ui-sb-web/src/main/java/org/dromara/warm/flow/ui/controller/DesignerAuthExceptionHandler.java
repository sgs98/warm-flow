package org.dromara.warm.flow.ui.controller;

import org.dromara.warm.flow.core.dto.ApiResult;
import org.dromara.warm.flow.ui.service.DesignerAuthException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 设计器鉴权失败统一映射为 HTTP 401，前端可据此跳转登录。
 *
 * @author may
 * @since 2.1.2
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class DesignerAuthExceptionHandler {

    @ExceptionHandler(DesignerAuthException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResult<Void> handle(DesignerAuthException e) {
        return ApiResult.fail(401, e.getMessage());
    }
}
