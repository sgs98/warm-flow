package org.dromara.warm.demo.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 流程错误触发请求。
 *
 * @author may
 * @since 2026/10/16
 */
@Getter
@Setter
public class ErrorTriggerRequest {

    /**
     * 错误码，匹配事件节点的 eventName；为空表示通配。
     */
    private String errorCode;

    /**
     * 错误描述，仅用于日志与异常信息。
     */
    private String message;
}
