package org.dromara.warm.demo.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 发起流程结果 VO。
 *
 * @author may
 * @since 2026/9/11
 */
@Getter
@Setter
public class StartInstanceVo {

    /**
     * 流程实例主键。
     */
    private Long instanceId;

    /**
     * 发起后当前待办任务主键。
     */
    private Long taskId;

    /**
     * 发起后当前待办节点名称。
     */
    private String nodeName;
}
