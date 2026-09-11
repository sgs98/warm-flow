package org.dromara.warm.demo.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * demo 用户 VO。
 *
 * @author may
 * @since 2026/9/5
 */
@Getter
@Setter
public class DemoUserVo {

    /**
     * 用户主键。
     */
    private Long id;

    /**
     * 用户编码。
     */
    private String userName;

    /**
     * 用户名称。
     */
    private String realName;

    /**
     * 角色类型。
     */
    private String roleType;

    /**
     * 部门编码。
     */
    private String deptCode;

    /**
     * 部门名称。
     */
    private String deptName;
}
