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

    List<DemoUserVo> listApprovers();

    List<DemoDepartmentVo> listDepartments();

    List<String> permissionFlags(String userName);

    /**
     * 根据节点办理权限标识查询可选用户。
     *
     * @param permissionFlags 节点办理权限标识集合
     * @return 可选用户集合
     */
    List<DemoUserVo> listByPermissionFlags(List<String> permissionFlags);
}
