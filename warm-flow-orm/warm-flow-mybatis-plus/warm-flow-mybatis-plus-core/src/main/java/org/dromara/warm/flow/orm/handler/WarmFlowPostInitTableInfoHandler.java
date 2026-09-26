package org.dromara.warm.flow.orm.handler;

import com.baomidou.mybatisplus.core.handlers.PostInitTableInfoHandler;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import org.apache.ibatis.session.Configuration;
import org.dromara.warm.flow.core.exception.FlowException;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

/**
 * MyBatis-Plus 逻辑删除开关处理器。
 * <p>
 * warm-flow 配置 logic-delete=true（默认）时不做任何处理，流程表走 MP 自身的
 * {@code @TableLogic} 逻辑删除；logic-delete=false 时在表信息初始化阶段关闭
 * 流程表的 withLogicDelete，使删除操作变为物理删除。
 * <p>
 * 注意：关闭后 MP 的查询也不再过滤 del_flag，切换物理删除模式前需先清理
 * 历史逻辑删除数据，否则已删数据会重新出现在查询结果中。
 *
 * @author may
 * @since 2026/9/26
 */
public class WarmFlowPostInitTableInfoHandler implements PostInitTableInfoHandler {

    private static final List<String> FLOW_TABLES = Arrays.asList("flow_definition", "flow_node",
        "flow_skip", "flow_instance", "flow_task", "flow_his_task", "flow_user");

    private final boolean logicDelete;

    public WarmFlowPostInitTableInfoHandler(boolean logicDelete) {
        this.logicDelete = logicDelete;
    }

    @Override
    public TableInfo postTableInfo(TableInfo tableInfo, Configuration configuration) {
        if (logicDelete || !FLOW_TABLES.contains(tableInfo.getTableName())) {
            return tableInfo;
        }
        try {
            Field field = tableInfo.getClass().getDeclaredField("withLogicDelete");
            field.setAccessible(true);
            // 关闭逻辑删除，删除操作变为物理删除
            field.set(tableInfo, false);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new FlowException("反射设置对象值异常");
        }
        return tableInfo;
    }
}
