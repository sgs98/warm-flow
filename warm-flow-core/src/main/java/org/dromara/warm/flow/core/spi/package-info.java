/*
 *    Copyright 2024-2025, Warm-Flow (290631660@qq.com).
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
/**
 * 引擎业务扩展点（SPI）契约包：宿主业务方通过实现这里的接口接入自身逻辑，
 * 全部以 Spring Bean / 容器注册方式生效，无任何类路径字符串配置。
 *
 * <ul>
 *   <li>{@link org.dromara.warm.flow.core.spi.Listener}：节点监听器，在节点属性面板按事件（创建/开始/分派/完成）配置</li>
 *   <li>{@link org.dromara.warm.flow.core.spi.GlobalListener}：全局监听器，实现后注册为 Bean 即对全部流程生效</li>
 *   <li>{@link org.dromara.warm.flow.core.spi.PermissionHandler}：办理人解析（当前用户 / 权限标识）</li>
 *   <li>{@link org.dromara.warm.flow.core.spi.DataFillHandler}：实体字段自动填充（主键、创建/更新时间等）</li>
 *   <li>{@link org.dromara.warm.flow.core.spi.TenantHandler}：多租户隔离</li>
 *   <li>{@link org.dromara.warm.flow.core.spi.StatusMapper}：状态语义 → 宿主编码映射</li>
 *   <li>{@link org.dromara.warm.flow.core.spi.ListenerVariable}：监听器上下文对象（Listener 接口参数，随契约提供）</li>
 * </ul>
 *
 * <p>引擎兜底实现见子包 {@code org.dromara.warm.flow.core.spi.impl}。
 * <p>表达式与网关策略（ExpressionStrategy 家族、GatewayStrategy）及其内置 / SpEL 实现属于
 * 引擎内部扩展区，见 {@code org.dromara.warm.flow.core.strategy}，业务方一般无需触碰。
 */
package org.dromara.warm.flow.core.spi;
