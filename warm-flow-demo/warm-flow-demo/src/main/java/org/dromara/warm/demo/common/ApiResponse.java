package org.dromara.warm.demo.common;

import lombok.Getter;
import lombok.Setter;

/**
 * 统一响应结构 {code, message, data}。
 *
 * @author may
 * @since 2026/9/5
 */
@Getter
@Setter
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, "操作成功", data);
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(200, "操作成功", null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
