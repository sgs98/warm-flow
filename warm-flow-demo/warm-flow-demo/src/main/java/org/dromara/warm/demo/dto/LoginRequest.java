package org.dromara.warm.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 登录请求。
 *
 * @author may
 * @since 2026/11/11
 */
@Getter
@Setter
public class LoginRequest {

    /**
     * 用户名。
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 密码。
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
