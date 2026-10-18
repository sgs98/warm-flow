package org.dromara.warm.demo.event;

import lombok.extern.slf4j.Slf4j;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.service.event.EventMessage;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事件消息订阅示例：演示宿主收到「消息抛出 / 消息结束」之后怎么做真实业务动作。
 *
 * <p>完整链路：流程走到 {@code MESSAGE_THROW} / {@code MESSAGE_END} 节点 → 引擎调用
 * {@link org.dromara.warm.flow.core.service.event.EventPublisher}（demo 由
 * {@code DemoEventPublisher} 实现）→ 发布器把消息转成 Spring 应用事件 → 本类消费。</p>
 *
 * <p>真实项目就写在这一层：发短信 / 邮件、调第三方 HTTP、发 MQ、启动下游流程；
 * 引擎只保证「消息到达时同步回调一次」，可靠投递与幂等由宿主负责（写 Outbox 表再异步投递、
 * 按 {@code eventWaitId} 或业务幂等键去重），因为这里是<b>至少一次</b>语义。</p>
 *
 * <p>demo 用「按消息启动下游流程 {@code demo_event_notify}」代替真实外部动作：跑完案例 3
 * 「账号注销-冷静期」后，事件测试页会多出一个「注销结果通知」实例，可直观看到消息抛出的作用。</p>
 *
 * @author may
 * @since 2026/10/17
 */
@Slf4j
@Component
public class DemoMessageSubscriber {

    /**
     * 下游流程编码：按消息 {@code membership.cancelled} 启动（见 demo_event_notify.json）。
     */
    private static final String DOWNSTREAM_FLOW_CODE = "demo_event_notify";

    /**
     * 已处理过的幂等键（demo 用内存 Set，真实项目用唯一索引 / Redis / 状态机去重）。
     */
    private final Set<String> handledKeys = ConcurrentHashMap.newKeySet();

    /**
     * 消费引擎抛出的消息。
     *
     * <p>默认同步执行（在流程事务内、与流程推进同一线程）。需要异步 / 削峰时在真实项目里
     * 改为投递 MQ 或加 {@code @Async}，但那样就失去「发布失败即回滚流程」的能力，需自行保证一致性。</p>
     *
     * @param message 引擎发布的事件消息（eventWaitId / eventType / eventName / instanceId / payload 等）
     */
    @EventListener
    public void onEventMessage(EventMessage message) {
        // 幂等键：源实例 + 事件名。同一实例重复投递同一事件时只处理一次。
        String idempotentKey = message.getInstanceId() + "#" + message.getEventName();
        if (!handledKeys.add(idempotentKey)) {
            log.info("[消息订阅示例] 重复消息已跳过 key={}", idempotentKey);
            return;
        }
        log.info("[消息订阅示例] 收到 {}（事件名 {}，源实例 {}，载荷 {}）"
            , message.getEventType(), message.getEventName(), message.getInstanceId(), message.getPayload());

        // 真实动作举例：发通知 / 调第三方 / 发 MQ。demo 用「驱动下游流程」代替。
        if ("membership.cancelled".equals(message.getEventName())) {
            startDownstreamFlow(message);
        }
    }

    /**
     * 用抛出的消息启动下游流程：宿主消费方回调引擎的标准写法（与
     * {@code POST /api/instances/message} 等价，见 {@code EventController#startByMessage}）。
     *
     * <p>注意两点真实语义：<br>
     * 1）引擎是同步回调、与上游流程同一事务——若这里让异常抛出（如 FlowException），上游流程会一起回滚，
     * 这适合「投递失败即不允许流程继续」的场景；demo 为了不因下游流程未导入而阻断上游，只告警不抛出。<br>
     * 2）{@code businessId} 由调用方指定且引擎不去重，重复调用会创建多个实例，业务幂等要靠自己的业务主键。</p>
     *
     * @param message 抛出的消息，其 payload 合并为下游流程的流程变量
     */
    private void startDownstreamFlow(EventMessage message) {
        WorkflowContext context = new WorkflowContext();
        // 无人值守的系统动作，操作人兜底为 system（下游流程的创建人 / 历史任务办理人）
        context.setHandler("system");
        try {
            Instance downstream = FlowEngine.insService().startByMessage("notify-" + message.getInstanceId()
                , DOWNSTREAM_FLOW_CODE, message.getEventName(), message.getPayload(), context);
            log.info("[消息订阅示例] 已按消息 {} 启动下游流程 {}，实例 {}（业务ID notify-{}）"
                , message.getEventName(), DOWNSTREAM_FLOW_CODE, downstream.getId(), message.getInstanceId());
        } catch (Exception e) {
            // 下游流程未导入 / 未发布 / 消息名不匹配时会走到这里，只告警，不影响上游流程提交
            log.warn("[消息订阅示例] 下游流程 {} 启动失败（未导入或未发布？）：{}"
                , DOWNSTREAM_FLOW_CODE, e.getMessage());
        }
    }
}
