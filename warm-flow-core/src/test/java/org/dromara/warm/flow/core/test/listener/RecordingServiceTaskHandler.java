package org.dromara.warm.flow.core.test.listener;

import org.dromara.warm.flow.core.service.task.ServiceTaskContext;
import org.dromara.warm.flow.core.service.task.ServiceTaskHandler;

import java.util.Map;

/**
 * 单测服务任务处理器：写入 svcFlag 变量并记录本次变量表。
 *
 * @author warm
 */
public class RecordingServiceTaskHandler implements ServiceTaskHandler {

    /**
     * 最近一次执行后的流程变量表。
     */
    public static volatile Map<String, Object> LAST_VARIABLES;

    @Override
    public void execute(ServiceTaskContext context) {
        context.setVariable("svcFlag", "ran");
        LAST_VARIABLES = context.getVariables();
    }
}
