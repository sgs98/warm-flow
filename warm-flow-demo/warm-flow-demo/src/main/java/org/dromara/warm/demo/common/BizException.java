package org.dromara.warm.demo.common;

import lombok.Getter;

/**
 * 业务异常，携带 HTTP 状态码与响应 code。
 *
 * @author may
 * @since 2026/9/5
 */
@Getter
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException badRequest(String message) {
        return new BizException(400, message);
    }

    public static BizException notFound(String message) {
        return new BizException(404, message);
    }
}
