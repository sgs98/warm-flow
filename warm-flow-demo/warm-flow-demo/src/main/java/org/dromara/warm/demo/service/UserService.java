package org.dromara.warm.demo.service;

import org.dromara.warm.demo.vo.DemoDepartmentVo;
import org.dromara.warm.demo.vo.DemoUserVo;

import java.util.List;

/**
 * demo 用户业务接口。
 *
 * @author may
 * @since 2026/9/11
 */
public interface UserService {

    /**
     * 查询所有可办理流程的用户。
     *
     * @return 可办理流程的用户集合
     */
    List<DemoUserVo> listApprovers();

    /**
     * 查询内存部门树。
     *
     * @return 部门集合
     */
    List<DemoDepartmentVo> listDepartments();

    /**
     * 查询用户在流程引擎中的权限标识。
     * <p>包含用户名、用户权限标识以及所属部门层级权限标识。</p>
     *
     * @param userName 用户名
     * @return 权限标识集合
     */
    List<String> permissionFlags(String userName);

    /**
     * 根据节点办理权限标识查询可选用户。
     *
     * @param permissionFlags 节点办理权限标识集合
     * @return 可选用户集合
     */
    List<DemoUserVo> listByPermissionFlags(List<String> permissionFlags);
}
