package org.dromara.warm.flow.core.service.task;

/**
 * 服务任务处理器。
 *
 * <p>处理器不得直接操作待办、历史或流程路由；业务结果通过上下文变量返回。</p>
 *
 * @author warm
 */
public interface ServiceTaskHandler {

    /**
     * 执行服务任务。
     *
     * @param context 服务任务上下文
     */
    void execute(ServiceTaskContext context);
}
