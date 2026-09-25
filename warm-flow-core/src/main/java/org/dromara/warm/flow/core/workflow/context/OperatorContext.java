package org.dromara.warm.flow.core.workflow.context;

import lombok.Getter;
import lombok.Setter;

/**
 * 流程操作操作者上下文。
 *
 * @author may
 */
@Getter
@Setter
public class OperatorContext {

    /**
     * 当前操作者唯一标识。
     */
    private String handler;

    /**
     * 是否忽略流程办理权限校验。
     */
    private boolean ignorePermission;

    /**
     * 是否忽略权限校验、受托人委派处理和会签/票签协作规则（true：单方办理即可推动节点流转）。
     */
    private boolean ignore;

    /**
     * 创建空的操作者上下文。
     */
    public OperatorContext() {
    }

    /**
     * 创建指定操作者的上下文。
     *
     * @param handler 当前操作者标识
     */
    public OperatorContext(String handler) {
        this.handler = handler;
    }
}
