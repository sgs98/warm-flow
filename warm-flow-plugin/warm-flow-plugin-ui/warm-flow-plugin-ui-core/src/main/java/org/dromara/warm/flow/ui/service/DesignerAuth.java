package org.dromara.warm.flow.ui.service;

import org.dromara.warm.flow.core.invoker.FrameInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 设计器接口统一鉴权入口。
 * <p>
 * 每次请求解析一次 {@link DesignerAuthHandler}：实现存在时按登录与否裁决，
 * 不存在时拒绝全部数据接口——设计器安全由宿主显式接入，不做静默放行。
 *
 * @author may
 * @since 2.1.2
 */
public final class DesignerAuth {

    private static final Logger log = LoggerFactory.getLogger(DesignerAuth.class);

    private DesignerAuth() {
    }

    /**
     * 数据接口统一裁决：读、写、办理都只要求已登录（方法级权限由宿主自行把关）。
     *
     * @throws DesignerAuthException 未通过鉴权
     */
    public static void check() {
        DesignerAuthHandler handler = FrameInvoker.getBean(DesignerAuthHandler.class);
        if (handler == null) {
            log.debug("DesignerAuthHandler 未配置，拒绝设计器数据接口访问");
            throw new DesignerAuthException("设计器接口未接入认证（未实现 DesignerAuthHandler），访问被拒绝");
        }
        boolean pass;
        try {
            pass = handler.isAuthorized();
        } catch (DesignerAuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("DesignerAuthHandler.isAuthorized 执行异常，拒绝访问", e);
            pass = false;
        }
        if (!pass) {
            throw new DesignerAuthException("未登录或无权访问设计器接口");
        }
    }
}
