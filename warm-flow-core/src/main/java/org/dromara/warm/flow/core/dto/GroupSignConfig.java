package org.dromara.warm.flow.core.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 节点 {@code node_config.groupSign} 组签配置。
 *
 * @author may
 */
@Getter
@Setter
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class GroupSignConfig {

    /**
     * 组间办理顺序：{@code parallel} 或 {@code sequence}。
     */
    private String order;

    /**
     * 审批组，数组顺序即组顺序。
     */
    private List<Group> groups = new ArrayList<>();

    /**
     * 单个审批组。
     */
    @Getter
    @Setter
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Group {

        /**
         * 节点内唯一的组标识。
         */
        private String id;

        /**
         * 展示名称。
         */
        private String name;

        /**
         * 由宿主解析为具体办理人的权限标识。
         */
        private List<String> members = new ArrayList<>();
    }
}
