package org.dromara.warm.demo.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * demo 部门 VO。
 *
 * @author may
 * @since 2026/9/11
 */
@Getter
@Setter
public class DemoDepartmentVo {

    /**
     * 部门编码。
     */
    private String code;

    /**
     * 部门名称。
     */
    private String name;

    /**
     * 父部门编码。
     */
    private String parentCode;
}
