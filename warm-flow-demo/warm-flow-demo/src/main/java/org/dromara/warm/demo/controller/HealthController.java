package org.dromara.warm.demo.controller;

import org.dromara.warm.demo.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查接口。
 *
 * @author may
 * @since 2026/9/5
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * 查询 Demo 服务健康状态。
     *
     * @return 服务状态信息
     */
    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        Map<String, String> data = new HashMap<>();
        data.put("status", "UP");
        data.put("service", "warm-flow-demo");
        return ApiResponse.ok(data);
    }
}
