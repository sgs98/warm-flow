package org.dromara.warm.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 消息启动流程请求。
 *
 * @author may
 * @since 2026/10/17
 */
@Getter
@Setter
public class MessageStartRequest {

    /**
     * 流程编码。
     */
    @NotBlank(message = "流程编码不能为空")
    private String flowCode;

    /**
     * 业务单据主键，用于幂等与业务关联。
     */
    @NotBlank(message = "业务单据主键不能为空")
    private String businessId;

    /**
     * 消息名称，匹配 MESSAGE_START 节点的 eventName。
     */
    @NotBlank(message = "消息名称不能为空")
    private String eventName;

    /**
     * 消息载荷，合并为流程变量。
     */
    private Map<String, Object> variables;
}
