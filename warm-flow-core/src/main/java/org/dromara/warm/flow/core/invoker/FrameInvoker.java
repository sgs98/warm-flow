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
package org.dromara.warm.flow.core.invoker;

import java.util.function.Function;

/**
 * 框架调用桥接器。
 * <p>
 * core 不直接依赖 Spring 等容器，通过这里注册的函数获取 Bean 和配置项。
 * Spring Boot 等适配模块在启动时注入对应函数，core 与 ORM/plugin 模块统一通过静态方法访问。
 *
 * @author warm
 */
public class FrameInvoker<M> {

    /**
     * 全局桥接器实例。
     */
    public static FrameInvoker frameInvoker = new FrameInvoker<>();

    /**
     * 按类型获取 Bean 的函数。
     */
    private Function<Class<M>, M> beanFunction;

    /**
     * 按配置 key 获取配置值的函数。
     */
    private Function<String, String> cfgFunction;

    public FrameInvoker() {
    }

    /**
     * 注册按类型获取 Bean 的函数。
     *
     * @param function Bean 获取函数
     * @param <M> Bean 类型
     */
    public static <M> void setBeanFunction(Function<Class<M>, M> function) {
        frameInvoker.beanFunction = function;
    }

    /**
     * 从外部框架容器获取 Bean。
     * <p>
     * 未注册函数或获取失败时返回 {@code null}，调用方需要按可选依赖处理。
     *
     * @param tClass Bean 类型
     * @param <M> Bean 类型
     * @return Bean 实例
     */
    public static <M> M getBean(Class<M> tClass) {
        try {
            return (M) frameInvoker.beanFunction.apply(tClass);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 注册按配置 key 获取配置值的函数。
     *
     * @param function 配置获取函数
     */
    public static void setCfgFunction(Function<String, String> function) {
        frameInvoker.cfgFunction = function;
    }

    /**
     * 从外部框架环境获取配置值。
     *
     * @param key 配置 key
     * @return 配置值；未注册函数或获取失败时返回 {@code null}
     */
    public static String getCfg(String key) {
        try {
            return (String) frameInvoker.cfgFunction.apply(key);
        } catch (Exception e) {
            return null;
        }
    }

}
