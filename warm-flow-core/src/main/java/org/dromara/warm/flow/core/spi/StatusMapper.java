package org.dromara.warm.flow.core.spi;

import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.TaskStatus;

/**
 * 状态映射扩展点：把引擎的三个语义维度翻译成宿主自己的编码。
 * <p>
 * 引擎内部只认语义，落库编码全部经由本接口解析：
 * <ul>
 *   <li>{@link #instanceStatus(FlowStatus)} → {@code flow_instance.flow_status}</li>
 *   <li>{@link #taskStatus(TaskStatus)} → {@code flow_task} / {@code flow_his_task} 的 {@code flow_status}</li>
 *   <li>{@link #cooperateStatus(CooperateType)} → 协作动作写入历史时的任务状态</li>
 * </ul>
 * 默认实现与引擎内置编码完全一致（{@link org.dromara.warm.flow.core.spi.impl.DefaultStatusMapper}）。宿主用自己的状态枚举时实现本接口，
 * 并在启动时注册 {@code FlowEngine.setStatusMapper(...)}，引擎各操作点即可直接写入宿主编码，
 * 无需每次调用手动传 {@code flowStatus} / {@code taskStatus}。
 * <p>
 * 手动传入的状态仍然优先，本接口只提供默认值。
 *
 * @author may
 */
public interface StatusMapper {

    /**
     * 实例状态语义转持久化编码。
     *
     * @param semantic 实例状态语义
     * @return 宿主状态编码
     */
    default String instanceStatus(FlowStatus semantic) {
        return semantic.getKey();
    }

    /**
     * 任务状态语义转持久化编码。
     *
     * @param semantic 任务状态语义
     * @return 宿主状态编码
     */
    default String taskStatus(TaskStatus semantic) {
        return semantic.getKey();
    }

    /**
     * 协作动作转任务状态编码。
     * <p>
     * 协作类型（转办 / 委派 / 加签 / 减签）是引擎的行为维度，由引擎固定维护；
     * 本方法只决定该动作在历史任务上留下的状态编码。默认回落「待办理」。
     *
     * @param type 协作类型
     * @return 宿主任务状态编码
     */
    default String cooperateStatus(CooperateType type) {
        return taskStatus(TaskStatus.WAITING);
    }

    /**
     * 判断编码是否为实例终态（实例不可再办理、不可再流转）。
     * <p>
     * 默认只认内置终态；宿主自定义编码时需重写本方法，引擎的状态守卫据此放行或拦截。
     *
     * @param code 宿主实例状态编码
     * @return 终态返回 {@code true}
     */
    default boolean isTerminal(String code) {
        return FlowStatus.isTerminalKey(code);
    }

    /**
     * 状态编码转展示文案。
     *
     * @param code 状态编码
     * @return 展示文案；未匹配时返回编码本身
     */
    default String label(String code) {
        String instance = FlowStatus.getValueByKey(code);
        if (instance != null) {
            return instance;
        }
        String task = TaskStatus.getValueByKey(code);
        return task == null ? code : task;
    }

}
