package org.dromara.warm.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.warm.flow.orm.entity.FlowDefinition;

/**
 * Demo 侧流程定义查询 Mapper。
 * <p>仅用于 demo 自身的关键字分页查询，复用引擎 ORM 实体映射 flow_definition，不改动引擎表结构。</p>
 *
 * @author may
 * @since 2026/9/5
 */
@Mapper
public interface DemoFlowDefinitionMapper extends BaseMapper<FlowDefinition> {
}
