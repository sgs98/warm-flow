package org.dromara.warm.demo.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 定时任务手动补发请求。
 * <p>只影响这一次补发的实例，不推进定时任务的等待记录。</p>
 *
 * @author may
 * @since 2026/10/21
 */
@Getter
@Setter
public class TimerStartRequest {

    /**
     * 业务单据主键，留空时由服务端按 {@code M{等待记录id}_{时间戳}} 生成。
     */
    private String businessId;

    /**
     * 额外流程变量，合并到事件节点默认变量之后。
     */
    private Map<String, Object> variables;
}
