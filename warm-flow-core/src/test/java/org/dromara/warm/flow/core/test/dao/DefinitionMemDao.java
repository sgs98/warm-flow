package org.dromara.warm.flow.core.test.dao;

import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.orm.dao.FlowDefinitionDao;

import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 单测内存实现：流程定义 DAO。
 *
 * @author warm
 */
public class DefinitionMemDao<T extends Definition> extends InMemoryDao<T> implements FlowDefinitionDao<T> {

    /**
     * 创建流程定义内存 DAO，并复用基类的实体工厂与调用日志。
     */
    public DefinitionMemDao(Supplier<T> factory, List<String> log) {
        super(factory, log);
    }

    /**
     * 按流程编码查询定义，并按版本号倒序返回，模拟定义加载时的版本选择依据。
     */
    @Override
    public List<T> queryByCodeList(List<String> codeList) {
        log.add(name() + ".queryByCodeList" + codeList);
        return store.values().stream()
                .filter(d -> codeList.contains(((Definition) d).getFlowCode()))
                .sorted(Comparator.comparing(Definition::getVersion).reversed())
                .map(this::copyOf)
                .collect(Collectors.toList());
    }

    /**
     * 批量更新流程定义发布状态，记录更新范围供生命周期测试断言。
     */
    @Override
    public void updatePublishStatus(List<Long> ids, Integer publishStatus) {
        log.add(name() + ".updatePublishStatus" + ids + ",status=" + publishStatus);
        for (Definition d : store.values().stream().map(Definition.class::cast).collect(Collectors.toList())) {
            if (ids.contains(d.getId())) {
                d.setIsPublish(publishStatus);
            }
        }
    }
}
