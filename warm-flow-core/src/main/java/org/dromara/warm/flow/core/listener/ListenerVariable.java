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
package org.dromara.warm.flow.core.listener;

import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

import java.util.List;
import java.util.Map;

/**
 * 监听器上下文变量。
 * <p>
 * 引擎在触发节点监听器或全局监听器时传入该对象，业务方可读取当前定义、实例、节点、
 * 任务、流程变量、后续节点和新建任务等上下文。部分事件阶段字段可能为空，监听器实现需按需判空。
 *
 * @author warm
 */
public class ListenerVariable {

    /**
     * 流程定义
     */
    private Definition definition;

    /**
     * 流程实例
     */
    private Instance instance;

    /**
     * 监听器对应的节点
     */
    private Node node;

    /**
     * 当前任务
     */
    private Task task;

    /**
     * 下一次执行的节点集合
     */
    private List<Node> nextNodes;

    /**
     * 新创建任务集合
     */
    private List<Task> nextTasks;

    /**
     * 流程变量
     */
    private Map<String, Object> variable;

    /**
     * 本次实际触发的监听器事件类型，取值见 {@link Listener} 事件常量
     */
    private String eventType;

    /**
     * 工作流内置参数
     */
    private WorkflowContext context;


    /**
     * 创建空监听器变量。
     */
    public ListenerVariable() {
    }

    /**
     * 创建定义、实例和流程变量维度的监听器变量。
     *
     * @param definition 流程定义
     * @param instance   流程实例
     * @param variable   流程变量
     */
    public ListenerVariable(Definition definition, Instance instance, Map<String, Object> variable) {
        this.definition = definition;
        this.instance = instance;
        this.variable = variable;
    }

    /**
     * 创建带当前节点的监听器变量。
     *
     * @param definition 流程定义
     * @param instance   流程实例
     * @param node       当前节点
     * @param variable   流程变量
     */
    public ListenerVariable(Definition definition, Instance instance, Node node, Map<String, Object> variable) {
        this.definition = definition;
        this.instance = instance;
        this.node = node;
        this.variable = variable;
    }

    /**
     * 创建带当前任务的监听器变量。
     *
     * @param definition 流程定义
     * @param instance   流程实例
     * @param variable   流程变量
     * @param task       当前任务
     */
    public ListenerVariable(Definition definition, Instance instance, Map<String, Object> variable, Task task) {
        this.definition = definition;
        this.instance = instance;
        this.variable = variable;
        this.task = task;
    }

    /**
     * 创建带当前节点和任务的监听器变量。
     *
     * @param definition 流程定义
     * @param instance   流程实例
     * @param node       当前节点
     * @param variable   流程变量
     * @param task       当前任务
     */
    public ListenerVariable(Definition definition, Instance instance, Node node, Map<String, Object> variable, Task task) {
        this.definition = definition;
        this.instance = instance;
        this.node = node;
        this.variable = variable;
        this.task = task;
    }

    /**
     * 创建带后续节点集合的监听器变量。
     *
     * @param definition 流程定义
     * @param instance   流程实例
     * @param node       当前节点
     * @param variable   流程变量
     * @param task       当前任务
     * @param nextNodes  后续节点集合
     */
    public ListenerVariable(Definition definition, Instance instance, Node node, Map<String, Object> variable, Task task, List<Node> nextNodes) {
        this.definition = definition;
        this.instance = instance;
        this.node = node;
        this.variable = variable;
        this.task = task;
        this.nextNodes = nextNodes;
    }

    /**
     * 创建带后续节点和新任务集合的监听器变量。
     *
     * @param definition 流程定义
     * @param instance   流程实例
     * @param node       当前节点
     * @param variable   流程变量
     * @param task       当前任务
     * @param nextNodes  后续节点集合
     * @param nextTasks  新创建任务集合
     */
    public ListenerVariable(Definition definition, Instance instance, Node node, Map<String, Object> variable, Task task
        , List<Node> nextNodes, List<Task> nextTasks) {
        this.definition = definition;
        this.instance = instance;
        this.node = node;
        this.variable = variable;
        this.task = task;
        this.nextNodes = nextNodes;
        this.nextTasks = nextTasks;
    }

    /**
     * 获取流程定义。
     *
     * @return 流程定义
     */
    public Definition getDefinition() {
        return definition;
    }

    /**
     * 设置流程定义。
     *
     * @param definition 流程定义
     * @return 当前监听器变量
     */
    public ListenerVariable setDefinition(Definition definition) {
        this.definition = definition;
        return this;
    }

    /**
     * 获取流程实例。
     *
     * @return 流程实例
     */
    public Instance getInstance() {
        return instance;
    }

    /**
     * 设置流程实例。
     *
     * @param instance 流程实例
     * @return 当前监听器变量
     */
    public ListenerVariable setInstance(Instance instance) {
        this.instance = instance;
        return this;
    }

    /**
     * 获取监听器对应节点。
     *
     * @return 节点
     */
    public Node getNode() {
        return node;
    }

    /**
     * 设置监听器对应节点。
     *
     * @param node 节点
     * @return 当前监听器变量
     */
    public ListenerVariable setNode(Node node) {
        this.node = node;
        return this;
    }

    /**
     * 获取当前任务。
     *
     * @return 当前任务
     */
    public Task getTask() {
        return task;
    }

    /**
     * 设置当前任务。
     *
     * @param task 当前任务
     * @return 当前监听器变量
     */
    public ListenerVariable setTask(Task task) {
        this.task = task;
        return this;
    }

    /**
     * 获取后续节点集合。
     *
     * @return 后续节点集合
     */
    public List<Node> getNextNodes() {
        return nextNodes;
    }

    /**
     * 设置后续节点集合。
     *
     * @param nextNodes 后续节点集合
     * @return 当前监听器变量
     */
    public ListenerVariable setNextNodes(List<Node> nextNodes) {
        this.nextNodes = nextNodes;
        return this;
    }

    /**
     * 获取新创建任务集合。
     *
     * @return 新任务集合
     */
    public List<Task> getNextTasks() {
        return nextTasks;
    }

    /**
     * 设置新创建任务集合。
     *
     * @param nextTasks 新任务集合
     * @return 当前监听器变量
     */
    public ListenerVariable setNextTasks(List<Task> nextTasks) {
        this.nextTasks = nextTasks;
        return this;
    }

    /**
     * 获取流程变量。
     *
     * @return 流程变量
     */
    public Map<String, Object> getVariable() {
        return variable;
    }

    /**
     * 设置流程变量。
     *
     * @param variable 流程变量
     * @return 当前监听器变量
     */
    public ListenerVariable setVariable(Map<String, Object> variable) {
        this.variable = variable;
        return this;
    }

    /**
     * 获取本次实际触发的监听器事件类型。
     *
     * @return 监听器事件类型，取值见 {@link Listener} 事件常量
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * 设置本次实际触发的监听器事件类型。
     *
     * @param eventType 监听器事件类型
     * @return 当前监听器变量
     */
    public ListenerVariable setEventType(String eventType) {
        this.eventType = eventType;
        return this;
    }

    /**
     * 获取流程动作执行上下文。
     *
     * @return 流程动作执行上下文
     */
    public WorkflowContext getContext() {
        return context;
    }

    /**
     * 设置流程动作执行上下文。
     *
     * @param context 流程动作执行上下文
     * @return 当前监听器变量对象
     */
    public ListenerVariable setContext(WorkflowContext context) {
        this.context = context;
        return this;
    }


    @Override
    public String toString() {
        return "ListenerVariable{" +
            "definition=" + definition +
            ", instance=" + instance +
            ", node=" + node +
            ", task=" + task +
            ", nextNodes=" + nextNodes +
            ", nextTasks=" + nextTasks +
            ", variable=" + variable +
            ", eventType='" + eventType + '\'' +
            ", context=" + context +
            '}';
    }
}
