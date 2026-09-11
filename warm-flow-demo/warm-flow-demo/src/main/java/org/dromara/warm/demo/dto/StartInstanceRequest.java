package org.dromara.warm.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 发起流程请求。
 *
 * @author may
 * @since 2026/9/5
 */
@Getter
@Setter
public class StartInstanceRequest {

    @NotBlank(message = "流程编码不能为空")
    private String flowCode;

    @NotBlank(message = "业务id不能为空")
    private String businessId;

    /**
     * 业务变量，写入实例 variable，条件跳转等可读取
     */
    private Map<String, Object> variables;
}
