package org.dromara.warm.demo.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.warm.demo.common.ApiResponse;
import org.dromara.warm.demo.service.UserService;
import org.dromara.warm.demo.vo.DemoUserVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * demo 用户接口（前端办理人选择等）。
 *
 * @author may
 * @since 2026/9/5
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 查询 Demo 可选办理用户。
     *
     * @return 用户集合
     */
    @GetMapping("/users")
    public ApiResponse<List<DemoUserVo>> users() {
        return ApiResponse.ok(userService.listApprovers());
    }
}
