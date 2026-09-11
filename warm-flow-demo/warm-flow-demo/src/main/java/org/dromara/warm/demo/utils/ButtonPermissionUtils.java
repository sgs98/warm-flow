package org.dromara.warm.demo.utils;

import lombok.extern.slf4j.Slf4j;
import org.dromara.warm.demo.enums.ButtonPermissionEnum;
import org.dromara.warm.demo.vo.ButtonPermissionVo;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 节点按钮权限工具。
 *
 * @author may
 * @since 2026/9/11
 */
@Slf4j
public final class ButtonPermissionUtils {

    private ButtonPermissionUtils() {
    }

    /**
     * 查询节点按钮权限。
     *
     * @param definitionId 流程定义主键
     * @param nodeCode     节点编码
     * @return 按钮权限集合
     */
    public static List<ButtonPermissionVo> list(Long definitionId, String nodeCode) {
        Map<ButtonPermissionEnum, Boolean> defaults = new EnumMap<>(ButtonPermissionEnum.class);
        for (ButtonPermissionEnum button : ButtonPermissionEnum.values()) {
            defaults.put(button, button.isDefaultShow());
        }
        try {
            Node node = FlowEngine.nodeService().getByDefIdAndNodeCode(definitionId, nodeCode);
            if (node != null && StringUtils.isNotEmpty(node.getExt())) {
                List<Map<String, Object>> extList = FlowEngine.jsonConvert.strToList(node.getExt());
                for (Map<String, Object> ext : extList) {
                    if (ButtonPermissionEnum.EXT_CODE.equals(String.valueOf(ext.get("code")))) {
                        Set<ButtonPermissionEnum> enabled = new HashSet<>();
                        String value = String.valueOf(ext.get("value"));
                        for (String item : value.split(",")) {
                            ButtonPermissionEnum button = ButtonPermissionEnum.fromCode(item.trim());
                            if (button != null) {
                                enabled.add(button);
                            }
                        }
                        for (ButtonPermissionEnum key : defaults.keySet()) {
                            defaults.put(key, enabled.contains(key));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析节点按钮权限失败，使用默认配置，definitionId={}, nodeCode={}", definitionId, nodeCode, e);
        }
        List<ButtonPermissionVo> result = new ArrayList<>();
        for (Map.Entry<ButtonPermissionEnum, Boolean> entry : defaults.entrySet()) {
            result.add(new ButtonPermissionVo(entry.getKey().getCode(), entry.getKey().getLabel(), entry.getValue()));
        }
        return result;
    }
}
