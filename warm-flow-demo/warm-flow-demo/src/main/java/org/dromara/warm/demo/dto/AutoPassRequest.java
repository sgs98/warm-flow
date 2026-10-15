package org.dromara.warm.demo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 流程定义自动审批开关请求。
 *
 * <p>自动审批开关存放在流程定义配置 {@code flow_definition.def_config} 的 {@code autoPass} 键上，
 * 属定义级运行时配置，引擎只读 def_config，不回退读业务扩展属性 ext。</p>
 *
 * @author may
 * @since 2026/10/15
 */
@Getter
@Setter
public class AutoPassRequest {

    /**
     * 是否开启同一办理人自动审批。
     */
    @NotNull(message = "自动审批开关不能为空")
    private Boolean autoPass;
}
