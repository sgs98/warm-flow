package org.dromara.warm.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 结束流程实例重置请求。
 *
 * @author may
 * @since 2026/11/6
 */
@Getter
@Setter
public class ResetInstanceRequest {

    /**
     * 重置目标普通人工审批节点编码。
     */
    @NotBlank(message = "目标节点编码不能为空")
    private String targetNodeCode;

    /**
     * 重置后的审批人权限标识。
     */
    @NotEmpty(message = "重置审批人不能为空")
    private List<@NotBlank(message = "审批人不能为空") String> handlers;

    /**
     * 重置后的流程实例状态，留空时默认为审批中。
     */
    private String flowStatus;
}
