package org.dromara.warm.demo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Demo 流程实例管理操作行锁。
 *
 * @author warm
 * @since 2026/11/6
 */
@Mapper
public interface DemoInstanceLockMapper {

    /**
     * 在当前事务内锁定流程实例，串行化同一实例的管理写操作。
     *
     * @param instanceId 流程实例主键
     * @return 命中的实例主键，不存在时返回 {@code null}
     */
    @Select("SELECT id FROM flow_instance WHERE id = #{instanceId} FOR UPDATE")
    Long lockById(@Param("instanceId") Long instanceId);
}
