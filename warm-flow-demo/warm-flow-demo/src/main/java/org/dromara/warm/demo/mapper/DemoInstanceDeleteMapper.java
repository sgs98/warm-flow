package org.dromara.warm.demo.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 流程实例物理删除 Mapper。
 * <p>仅 demo 演示物理清数使用，正式业务建议优先使用引擎逻辑删除。</p>
 *
 * @author may
 * @since 2026/9/11
 */
@Mapper
public interface DemoInstanceDeleteMapper {

    /**
     * 物理删除实例关联的流程用户数据。
     *
     * @param instanceId 流程实例主键
     * @return 删除行数
     */
    @Delete("DELETE u FROM flow_user u " +
        "LEFT JOIN flow_task t ON u.associated = t.id " +
        "LEFT JOIN flow_his_task h ON u.associated = h.id " +
        "LEFT JOIN flow_his_task ht ON u.associated = ht.task_id " +
        "WHERE t.instance_id = #{instanceId} OR h.instance_id = #{instanceId} " +
        "OR ht.instance_id = #{instanceId} OR u.associated = #{instanceId}")
    int deleteUsers(@Param("instanceId") Long instanceId);

    /**
     * 物理删除实例待办任务。
     *
     * @param instanceId 流程实例主键
     * @return 删除行数
     */
    @Delete("DELETE FROM flow_task WHERE instance_id = #{instanceId}")
    int deleteTasks(@Param("instanceId") Long instanceId);

    /**
     * 物理删除实例历史任务。
     *
     * @param instanceId 流程实例主键
     * @return 删除行数
     */
    @Delete("DELETE FROM flow_his_task WHERE instance_id = #{instanceId}")
    int deleteHisTasks(@Param("instanceId") Long instanceId);

    /**
     * 物理删除流程实例。
     *
     * @param instanceId 流程实例主键
     * @return 删除行数
     */
    @Delete("DELETE FROM flow_instance WHERE id = #{instanceId}")
    int deleteInstance(@Param("instanceId") Long instanceId);
}
