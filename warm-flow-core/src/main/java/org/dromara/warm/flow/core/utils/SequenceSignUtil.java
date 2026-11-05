package org.dromara.warm.flow.core.utils;

import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.UserType;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 顺序会签（顺签）工具：按 {@code flow_user.seq_no} 确定当前位办理人。
 *
 * <p>存量数据 seq_no 为空时按 id 升序兜底；同一顺序号下转办 / 委派记录优先，
 * 保证「转办承接当前位」的语义。</p>
 *
 * @author may
 */
public final class SequenceSignUtil {

    private SequenceSignUtil() {
    }

    /**
     * 取顺序号最小的办理人（当前位）。
     *
     * @param users 当前任务的办理人集合
     * @return 当前位办理人；集合为空时返回 {@code null}
     */
    public static User firstBySeqNo(List<User> users) {
        if (CollUtil.isEmpty(users)) {
            return null;
        }
        return users.stream().min(comparator(users)).orElse(null);
    }

    /**
     * 按整个任务的排序规则，取指定办理人占用的第一个顺序位。
     *
     * @param users   当前任务的办理人集合
     * @param handler 办理人标识
     * @return 该办理人的第一个顺序位；不存在时返回 {@code null}
     */
    public static User firstByHandler(List<User> users, String handler) {
        if (CollUtil.isEmpty(users)) {
            return null;
        }
        Comparator<User> comparator = comparator(users);
        return users.stream().filter(user -> Objects.equals(user.getProcessedBy(), handler))
            .min(comparator).orElse(null);
    }

    /**
     * 判断指定办理人是否为当前位。
     *
     * @param users   当前任务的办理人集合
     * @param handler 办理人标识
     * @return true：是当前位
     */
    public static boolean isCurrentPosition(List<User> users, String handler) {
        User current = firstBySeqNo(users);
        return current != null && Objects.equals(current.getProcessedBy(), handler);
    }

    private static Comparator<User> comparator(List<User> users) {
        return users.stream().anyMatch(user -> ObjectUtil.isNull(user.getSeqNo()))
            ? Comparator.comparing(User::getId, Comparator.nullsLast(Long::compareTo))
            : Comparator.comparing(User::getSeqNo)
                .thenComparing(user -> isTransferOrDepute(user) ? 0 : 1)
                .thenComparing(User::getId, Comparator.nullsLast(Long::compareTo));
    }

    private static boolean isTransferOrDepute(User user) {
        return UserType.TRANSFER.getKey().equals(user.getType())
            || UserType.DEPUTE.getKey().equals(user.getType());
    }
}
