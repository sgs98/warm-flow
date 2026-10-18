package org.dromara.warm.demo.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.warm.flow.core.service.event.EventMessage;
import org.dromara.warm.flow.core.service.event.EventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Demo 消息发布器：把 {@code MESSAGE_THROW} / {@code MESSAGE_END} 到达时的消息
 * 记录到内存环形队列并打印日志，供演示观察消息事件的真实发布。
 * <p>真实业务应替换为 HTTP 回调、消息队列或业务事件总线，并按幂等键去重。
 * demo 这里把消息再转成 Spring 应用事件，由 {@code DemoMessageSubscriber} 演示「宿主消费抛出消息」。</p>
 *
 * @author may
 * @since 2026/10/17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoEventPublisher implements EventPublisher {

    /**
     * 内存保留的最大消息条数。
     */
    private static final int MAX_RETAINED = 100;

    /**
     * Spring 应用事件发布器：把引擎消息转成应用事件，交给业务侧 {@code @EventListener} 消费
     * （见 {@code org.dromara.warm.demo.event.DemoMessageSubscriber}）。
     * <p>真实项目里这一步就是「投递」本身：写 Outbox 表、发 MQ、调第三方 HTTP 回调。</p>
     */
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 发布记录环形队列。
     */
    private final Deque<String> published = new ArrayDeque<>(MAX_RETAINED);

    @Override
    public void publish(EventMessage message) {
        String line = String.format("[%s] %s(%s) instance=%s node=%s payload=%s"
            , new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(message.getTriggerTime())
            , message.getEventType(), message.getEventName()
            , message.getInstanceId(), message.getNodeCode(), message.getPayload());
        log.info("Demo 消息发布: {}", line);
        synchronized (published) {
            if (published.size() >= MAX_RETAINED) {
                published.pollFirst();
            }
            published.addLast(line);
        }
        // 发布器只负责「投递」，业务动作交给订阅方：这里转成 Spring 应用事件（同步、与流程同一事务）。
        // 换成真实投递时，把下面一行替换为 mqTemplate.send(...) / restClient.post(...) / outboxMapper.insert(...) 即可。
        applicationEventPublisher.publishEvent(message);
    }

    /**
     * 查询最近的发布记录（新消息在前）。
     *
     * @return 发布记录列表
     */
    public List<String> recent() {
        synchronized (published) {
            List<String> list = new ArrayList<>(published);
            Collections.reverse(list);
            return list;
        }
    }
}
