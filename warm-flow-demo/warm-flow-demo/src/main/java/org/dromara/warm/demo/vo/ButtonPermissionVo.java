package org.dromara.warm.demo.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 节点按钮权限 VO。
 *
 * @author may
 * @since 2026/9/6
 */
@Getter
@AllArgsConstructor
public class ButtonPermissionVo {

    /**
     * 按钮权限编码。
     */
    private String code;

    /**
     * 按钮权限名称。
     */
    private String label;

    /**
     * 是否显示按钮。
     */
    private boolean show;
}
