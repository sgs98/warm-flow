package org.dromara.warm.demo.enums;

import lombok.Getter;

/**
 * 节点按钮权限。
 *
 * @author may
 * @since 2026/9/11
 */
@Getter
public enum ButtonPermissionEnum {
    POP("pop", "是否弹窗选人", false),
    TRUST("trust", "是否能委托", false),
    TRANSFER("transfer", "是否能转办", false),
    COPY("copy", "是否能抄送", true),
    BACK("back", "是否显示退回", true),
    ADD_SIGN("addSign", "是否能加签", false),
    SUB_SIGN("subSign", "是否能减签", false),
    TERMINATION("termination", "是否能终止", true),
    FILE("file", "是否能上传附件", true);

    public static final String PANEL_CODE = "buttonPermission";
    public static final String EXT_CODE = "ButtonPermissionEnum";

    private final String code;
    private final String label;
    private final boolean defaultShow;

    ButtonPermissionEnum(String code, String label, boolean defaultShow) {
        this.code = code;
        this.label = label;
        this.defaultShow = defaultShow;
    }

    public static ButtonPermissionEnum fromCode(String code) {
        for (ButtonPermissionEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
