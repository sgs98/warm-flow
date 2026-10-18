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
 * 引擎扩展点（SPI）契约包：业务方与插件模块通过实现这里的接口扩展引擎行为，
 * 全部以 Spring Bean / 容器注册方式生效，无任何类路径字符串配置。
 *
 * <h2>一级扩展点（业务方常用）</h2>
 * <ul>
 *   <li>{@link org.dromara.warm.flow.core.spi.Listener}：节点监听器，在节点属性面板按事件（创建/开始/分派/完成）配置</li>
 *   <li>{@link org.dromara.warm.flow.core.spi.GlobalListener}：全局监听器，实现后注册为 Bean 即对全部流程生效</li>
 *   <li>{@link org.dromara.warm.flow.core.spi.PermissionHandler}：办理人解析（当前用户 / 权限标识）</li>
 *   <li>{@link org.dromara.warm.flow.core.spi.DataFillHandler}：实体字段自动填充（主键、创建/更新时间等）</li>
 *   <li>{@link org.dromara.warm.flow.core.spi.TenantHandler}：多租户隔离</li>
 *   <li>{@link org.dromara.warm.flow.core.spi.StatusMapper}：状态语义 → 宿主编码映射</li>
 * </ul>
 *
 * <h2>二级扩展点（表达式策略插槽，业务方一般无需实现）</h2>
 * <p>默认由 {@code warm-flow-plugin-modes-sb} 提供基于 SpEL 的实现；仅当需要更换
 * 表达式引擎（如 QLExpress / Groovy）时才实现：
 * <ul>
 *   <li>{@link org.dromara.warm.flow.core.spi.ExpressionStrategy}：表达式策略基接口（按类型前缀路由，后注册优先）</li>
 *   <li>ConditionStrategy / HandlerStrategy / ListenerStrategy / ServiceTaskStrategy / VoteSignStrategy：条件、办理人、监听器、服务任务、票签各表达域</li>
 * </ul>
 *
 * <p>引擎兜底实现见子包 {@code org.dromara.warm.flow.core.spi.impl}；
 * 网关路由策略（GatewayStrategy）与内置串行/并行/包容实现属于引擎内部，
 * 见 {@code org.dromara.warm.flow.core.strategy}，不作为扩展点开放。
 */
package org.dromara.warm.flow.core.spi;
