package org.dromara.warm.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 任务操作请求（通过/退回/转办/委派/加签/减签）。
 *
 * @author may
 * @since 2026/9/5
 */
@Getter
@Setter
public class TaskActionRequest {

    /**
     * 当前操作人 user_name。
     */
    @NotBlank(message = "操作人不能为空")
    private String user;

    /**
     * 待办任务主键。
     */
    @NotNull(message = "任务主键不能为空")
    private Long taskId;

    /**
     * 流程实例主键。
     */
    private Long instanceId;

    /**
     * 办理意见
     */
    private String message;

    /**
     * 任意跳转目标节点编码（passAtWill/rejectAtWill）
     */
    private String nodeCode;

    /**
     * 转办/委派目标办理人（user_name）
     */
    private List<String> nextHandlers;

    /**
     * 下一节点编码 -> 办理人集合（user_name）。
     */
    private Map<String, List<String>> nextHandlerMap;

    /**
     * 加签办理人列表
     */
    private List<String> addHandlers;

    /**
     * 减签办理人列表
     */
    private List<String> reductionHandlers;

    /**
     * 业务变量
     */
    private Map<String, Object> variables;

    /**
     * 抄送人集合（user_name）。
     */
    private List<String> copyUsers;
}
