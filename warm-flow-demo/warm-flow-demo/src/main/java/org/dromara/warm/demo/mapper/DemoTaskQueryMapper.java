package org.dromara.warm.demo.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.warm.flow.orm.entity.FlowTask;
import org.dromara.warm.flow.orm.entity.FlowHisTask;

import java.util.List;

/**
 * 任务查询 Mapper。
 * <p>通过 flow_user 反查当前用户的待办与抄送，仅 demo 自身使用。</p>
 *
 * @author may
 * @since 2026/9/5
 */
@Mapper
public interface DemoTaskQueryMapper {

    @Select("<script>SELECT t.* FROM flow_task t WHERE t.del_flag = '0' AND EXISTS (" +
        "SELECT 1 FROM flow_user u WHERE u.associated = t.id AND u.processed_by IN " +
        "<foreach collection='permissions' item='permission' open='(' separator=',' close=')'>#{permission}</foreach> " +
        "AND u.type IN ('1', '2', '3') AND u.del_flag = '0') ORDER BY t.id DESC</script>")
    List<FlowTask> selectTodoPage(Page<FlowTask> page, @Param("permissions") List<String> permissions);

    /**
     * 分页查询当前用户收到的抄送历史。
     *
     * @param page        MyBatis Plus 分页对象
     * @param permissions 当前用户权限标识
     * @return 抄送历史集合
     */
    @Select("<script>SELECT h.* FROM flow_his_task h WHERE h.del_flag = '0' AND EXISTS (" +
        "SELECT 1 FROM flow_user u WHERE u.associated = h.task_id AND u.processed_by IN " +
        "<foreach collection='permissions' item='permission' open='(' separator=',' close=')'>#{permission}</foreach> " +
        "AND u.type = '4' AND u.del_flag = '0') ORDER BY h.id DESC</script>")
    List<FlowHisTask> selectCopyPage(Page<FlowHisTask> page, @Param("permissions") List<String> permissions);
}
