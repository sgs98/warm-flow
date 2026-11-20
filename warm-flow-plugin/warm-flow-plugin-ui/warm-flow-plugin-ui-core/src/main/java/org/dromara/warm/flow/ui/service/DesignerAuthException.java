package org.dromara.warm.flow.ui.service;

/**
 * 设计器接口未通过鉴权时抛出，由 web 适配模块映射为 HTTP 401。
 *
 * @author may
 * @since 2.1.2
 */
public class DesignerAuthException extends RuntimeException {

    public DesignerAuthException(String message) {
        super(message);
    }
}
