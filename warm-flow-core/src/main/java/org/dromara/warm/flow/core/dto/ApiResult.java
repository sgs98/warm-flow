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
package org.dromara.warm.flow.core.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通用响应结果。
 * <p>
 * 用于 UI 或适配层统一承载状态码、提示消息和业务数据，不参与流程引擎内部状态流转。
 *
 * @author ruoyi
 */
@Setter
@Getter
public class ApiResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 默认成功状态码。
     */
    public static final int SUCCESS = 200;

    /**
     * 默认失败状态码。
     */
    public static final int FAIL = 500;

    /**
     * 响应状态码。
     */
    private int code;

    /**
     * 响应提示消息。
     */
    private String msg;

    /**
     * 响应业务数据。
     */
    private T data;

    /**
     * 创建不带业务数据的成功响应。
     *
     * @param <T> 业务数据类型
     * @return 成功响应
     */
    public static <T> ApiResult<T> ok() {
        return restResult(null, SUCCESS, "操作成功");
    }

    /**
     * 创建带业务数据的成功响应。
     *
     * @param data 业务数据
     * @param <T>  业务数据类型
     * @return 成功响应
     */
    public static <T> ApiResult<T> ok(T data) {
        return restResult(data, SUCCESS, "操作成功");
    }

    /**
     * 创建带业务数据和自定义消息的成功响应。
     *
     * @param data 业务数据
     * @param msg  提示消息
     * @param <T>  业务数据类型
     * @return 成功响应
     */
    public static <T> ApiResult<T> ok(T data, String msg) {
        return restResult(data, SUCCESS, msg);
    }

    /**
     * 创建不带业务数据的默认失败响应。
     *
     * @param <T> 业务数据类型
     * @return 失败响应
     */
    public static <T> ApiResult<T> fail() {
        return restResult(null, FAIL, "操作失败");
    }

    /**
     * 创建带自定义消息的失败响应。
     *
     * @param msg 失败消息
     * @param <T> 业务数据类型
     * @return 失败响应
     */
    public static <T> ApiResult<T> fail(String msg) {
        return restResult(null, FAIL, msg);
    }

    /**
     * 创建带业务数据的默认失败响应。
     *
     * @param data 业务数据
     * @param <T>  业务数据类型
     * @return 失败响应
     */
    public static <T> ApiResult<T> fail(T data) {
        return restResult(data, FAIL, "操作失败");
    }

    /**
     * 创建带业务数据和自定义消息的失败响应。
     *
     * @param data 业务数据
     * @param msg  失败消息
     * @param <T>  业务数据类型
     * @return 失败响应
     */
    public static <T> ApiResult<T> fail(T data, String msg) {
        return restResult(data, FAIL, msg);
    }

    /**
     * 创建带自定义状态码和消息的失败响应。
     *
     * @param code 状态码
     * @param msg  失败消息
     * @param <T>  业务数据类型
     * @return 失败响应
     */
    public static <T> ApiResult<T> fail(int code, String msg) {
        return restResult(null, code, msg);
    }

    /**
     * 组装统一响应对象。
     *
     * @param data 业务数据
     * @param code 状态码
     * @param msg  提示消息
     * @param <T>  业务数据类型
     * @return 响应对象
     */
    private static <T> ApiResult<T> restResult(T data, int code, String msg) {
        ApiResult<T> apiResult = new ApiResult<>();
        apiResult.setCode(code);
        apiResult.setData(data);
        apiResult.setMsg(msg);
        return apiResult;
    }

    /**
     * 判断响应是否为失败结果。
     *
     * @param ret 响应对象
     * @param <T> 业务数据类型
     * @return 非成功状态码时返回 {@code true}
     */
    public static <T> Boolean isError(ApiResult<T> ret) {
        return !isSuccess(ret);
    }

    /**
     * 判断响应是否为默认成功结果。
     *
     * @param ret 响应对象
     * @param <T> 业务数据类型
     * @return 状态码等于 {@link #SUCCESS} 时返回 {@code true}
     */
    public static <T> Boolean isSuccess(ApiResult<T> ret) {
        return ApiResult.SUCCESS == ret.getCode();
    }
}
