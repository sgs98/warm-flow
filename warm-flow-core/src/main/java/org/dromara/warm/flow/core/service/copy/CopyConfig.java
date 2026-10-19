package org.dromara.warm.flow.core.service.copy;

import lombok.Getter;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.MapUtil;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 节点抄送配置：解析 {@code node_config.copy} 段。
 * <p>
 * 结构：
 * <pre>
 * {
 *   "copy": { "handlers": ["1", "2"] }
 * }
 * </pre>
 * 语义是「流程到达该节点时，把这条待办抄送给这些人」。抄送只写留痕，不产生待办、不改流程状态，
 * 也不参与任务归属；{@code handlers} 与办理人同构——由设计器产出（带类型前缀的入库标识），
 * 外加发起人占位（{@link org.dromara.warm.flow.core.constant.FlowCons#WARMFLOWINITIATOR}）。
 * <p>
 * 抄送不参与办理，收件人必须是具体用户，所以执行时统一交给
 * {@code PermissionHandler#resolveCopyHandlers} 展开成人：配置里可以是 {@code user:zhangsan}、
 * {@code dept:001}，宿主负责换成一个或多个真实用户，换不到人的标识直接丢弃。
 *
 * @author may
 */
@Getter
public class CopyConfig {

    /**
     * 抄送人标识集合；为空表示该节点不抄送。
     */
    private final List<String> handlers;

    private CopyConfig(List<String> handlers) {
        this.handlers = handlers;
    }

    /**
     * 解析节点抄送配置，格式宽松：空配置、缺少 copy 段、handlers 为空都表示不抄送。
     *
     * @param nodeConfig 节点配置 JSON
     * @return 抄送配置
     */
    @SuppressWarnings("unchecked")
    public static CopyConfig parse(String nodeConfig) {
        if (StringUtils.isEmpty(nodeConfig)) {
            return empty();
        }
        Map<String, Object> root = FlowEngine.jsonConvert.strToMap(nodeConfig);
        if (MapUtil.isEmpty(root)) {
            return empty();
        }
        Object section = root.get("copy");
        if (!(section instanceof Map)) {
            return empty();
        }
        Object handlers = ((Map<String, Object>) section).get("handlers");
        if (!(handlers instanceof List)) {
            return empty();
        }
        List<String> result = new ArrayList<>();
        for (Object handler : (List<Object>) handlers) {
            String value = handler == null ? null : String.valueOf(handler);
            if (StringUtils.isNotEmpty(value) && !result.contains(value)) {
                result.add(value);
            }
        }
        return new CopyConfig(result);
    }

    /**
     * 是否配置了抄送人。
     *
     * @return true：需要抄送
     */
    public boolean enabled() {
        return CollUtil.isNotEmpty(handlers);
    }

    /**
     * 空配置。
     *
     * @return 不含抄送人的配置
     */
    public static CopyConfig empty() {
        return new CopyConfig(List.of());
    }
}
