package org.dromara.warm.demo.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.warm.demo.common.ApiResponse;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 兜底错误处理器：让未匹配到接口的 404 也返回统一 {code, message, data}。
 *
 * @author may
 * @since 2026/9/5
 */
@RestController
public class GlobalErrorController implements ErrorController {

    /**
     * 将未进入业务异常处理器的错误转换为统一响应结构。
     *
     * @param request  当前请求
     * @param response 当前响应
     * @return 统一错误响应
     */
    @RequestMapping("/error")
    public ApiResponse<Void> error(HttpServletRequest request, HttpServletResponse response) {
        Object statusObj = request.getAttribute("jakarta.servlet.error.status_code");
        int status = statusObj == null ? 500 : Integer.parseInt(String.valueOf(statusObj));
        response.setStatus(status);
        String message = status == 404 ? "接口不存在" : "请求处理失败";
        return ApiResponse.fail(status, message);
    }
}
