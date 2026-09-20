package org.dromara.warm.flow.core.test.dao;

import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.orm.dao.FlowTaskDao;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 单测内存实现：待办任务 DAO。
 *
 * @author warm
 */
public class TaskMemDao<T extends Task> extends InMemoryDao<T> implements FlowTaskDao<T> {

    /**
     * 创建待办任务内存 DAO，并复用基类的实体工厂与调用日志。
     */
    public TaskMemDao(Supplier<T> factory, List<String> log) {
        super(factory, log);
    }

    /**
     * 删除指定流程实例关联的全部待办任务。
     */
    @Override
    public int deleteByInsIds(List<Long> instanceIds) {
        log.add(name() + ".deleteByInsIds" + instanceIds);
        List<Long> keys = store.values().stream().map(Task.class::cast)
                .filter(t -> instanceIds.contains(t.getInstanceId()))
                .map(Task::getId)
                .collect(Collectors.toList());
        keys.forEach(store::remove);
        return keys.size();
    }

    /**
     * 按实例 ID 与节点编码集合查询待办任务。
     */
    @Override
    public List<T> getByInsIdAndNodeCodes(Long instanceId, List<String> nodeCodes) {
        log.add(name() + ".getByInsIdAndNodeCodes[instanceId=" + instanceId + ",nodeCodes=" + nodeCodes + "]");
        return store.values().stream()
                .filter(t -> {
                    Task task = (Task) t;
                    return Objects.equals(task.getInstanceId(), instanceId) && nodeCodes.contains(task.getNodeCode());
                })
                .map(this::copyOf)
                .collect(Collectors.toList());
    }
}
