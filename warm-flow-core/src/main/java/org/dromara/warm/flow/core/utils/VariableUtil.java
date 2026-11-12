
package org.dromara.warm.flow.core.utils;
import org.dromara.warm.flow.core.constant.FlowCons;
import org.dromara.warm.flow.core.exception.FlowException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 引擎保留变量保护：{@code _wf_} 前缀的键只能由引擎内部写入。
 *
 * @author may
 */
public final class VariableUtil {

    private VariableUtil() {
    }

    /**
     * 是否为引擎保留变量。
     *
     * @param key 变量名
     * @return 保留返回 {@code true}
     */
    public static boolean isReserved(String key) {
        return key != null && key.startsWith(FlowCons.WF_RESERVED_PREFIX);
    }

    /**
     * 过滤业务入参中的保留变量；没有保留键时直接返回原集合，避免热路径复制。
     *
     * @param variables 业务变量
     * @return 过滤后的变量
     */
    public static Map<String, Object> filterReserved(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()
            || variables.keySet().stream().noneMatch(VariableUtil::isReserved)) {
            return variables;
        }
        Map<String, Object> filtered = new LinkedHashMap<>(variables);
        filtered.keySet().removeIf(VariableUtil::isReserved);
        return filtered;
    }

    /**
     * 拒绝业务代码写入引擎保留变量。
     *
     * @param key 变量名
     */
    public static void assertNotReserved(String key) {
        if (isReserved(key)) {
            throw new FlowException("变量名以 " + FlowCons.WF_RESERVED_PREFIX + " 开头，为引擎保留：" + key);
        }
    }
}
