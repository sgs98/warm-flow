package org.dromara.warm.demo.config;

/**
 * Demo 请求级用户上下文。
 * <p>按请求保存当前用户，供引擎 {@code PermissionHandler} 在办理权限校验时读取，
 * 由 {@link DemoUserInterceptor} 写入与清理。</p>
 *
 * @author may
 */
public final class DemoUserContext {

    /**
     * 当前请求用户名。
     */
    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();

    private DemoUserContext() {
    }

    /**
     * 保存当前请求用户名。
     *
     * @param userName 当前请求用户名
     */
    public static void set(String userName) {
        CURRENT_USER.set(userName);
    }

    /**
     * 获取当前请求用户名。
     *
     * @return 当前请求用户名；未设置时返回 {@code null}
     */
    public static String get() {
        return CURRENT_USER.get();
    }

    /**
     * 清理当前请求用户名，避免线程复用导致串号。
     */
    public static void clear() {
        CURRENT_USER.remove();
    }
}
