package org.dromara.warm.demo.config;

import org.dromara.warm.flow.ui.service.DesignerAuthHandler;
import org.springframework.stereotype.Component;

/**
 * 设计器鉴权 SPI 的 Demo 实现：校验登录 Cookie 的签名与有效期。
 * <p>身份来自 {@link DemoAuthInterceptor} 从 Cookie 解析并写入 {@link DemoUserContext} 的用户，
 * 不再接受可伪造的 {@code X-User-Name} 请求头。</p>
 *
 * @author may
 * @since 2026/11/11
 */
@Component
public class DemoDesignerAuthHandler implements DesignerAuthHandler {

    @Override
    public boolean isAuthorized() {
        return DemoUserContext.get() != null;
    }
}
