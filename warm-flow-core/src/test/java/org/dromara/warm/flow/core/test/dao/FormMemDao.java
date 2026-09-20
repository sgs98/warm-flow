package org.dromara.warm.flow.core.test.dao;

import org.dromara.warm.flow.core.entity.Form;
import org.dromara.warm.flow.core.orm.dao.FlowFormDao;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 单测内存实现：流程表单 DAO。
 *
 * @author warm
 */
public class FormMemDao<T extends Form> extends InMemoryDao<T> implements FlowFormDao<T> {

    /**
     * 创建流程表单内存 DAO，并复用基类的实体工厂与调用日志。
     */
    public FormMemDao(Supplier<T> factory, List<String> log) {
        super(factory, log);
    }

    /**
     * 按表单编码集合查询表单，返回持久化字段拷贝。
     */
    @Override
    public List<T> queryByCodeList(List<String> formCodeList) {
        log.add(name() + ".queryByCodeList" + formCodeList);
        return store.values().stream()
                .filter(f -> formCodeList.contains(((Form) f).getFormCode()))
                .map(this::copyOf)
                .collect(Collectors.toList());
    }
}
