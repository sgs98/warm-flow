package org.dromara.warm.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 消息事件触发请求。
 *
 * @author may
 * @since 2026/10/16
 */
@Getter
@Setter
public class MessageTriggerRequest {

    /**
     * 消息名称，匹配事件节点的 eventName。
     */
    @NotBlank(message = "消息名称不能为空")
    private String eventName;

    /**
     * 消息载荷，合并为流程变量。
     */
    private Map<String, Object> payload;
}
