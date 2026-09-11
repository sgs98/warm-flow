package org.dromara.warm.demo.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 任务可选节点 VO。
 *
 * @author may
 * @since 2026/9/11
 */
@Getter
@Setter
public class TaskNodeVo {

    /**
     * 节点编码。
     */
    private String nodeCode;

    /**
     * 节点名称。
     */
    private String nodeName;

    /**
     * 节点类型。
     *
     * @see org.dromara.warm.flow.core.enums.NodeType
     */
    private Integer nodeType;

    /**
     * 节点默认办理权限标识。
     */
    private List<String> permissionFlags;

    /**
     * 根据节点默认办理权限解析出的可选用户。
     */
    private List<DemoUserVo> selectableUsers;
}
