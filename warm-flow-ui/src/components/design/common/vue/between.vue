<template>
  <div class="between">
    <el-form ref="formRef" class="betweenForm" :model="form" label-width="110px" :rules="rules" :disabled="disabled" size="small">
      <!-- 页签区域 -->
      <div class="modern-tabs-wrapper">
        <div class="modern-tabs">
          <div
            v-for="(item, index) in tabsList"
            :key="item.name"
            class="modern-tab-item"
            :class="{ 'is-active': tabsValue === item.name, 'is-ext-tab': index >= 3 }"
            @click="tabsValue = item.name; handleTabChange(item.name)"
          >
            <svg class="tab-icon" viewBox="0 0 24 24"><path :d="item.iconPath || TAB_ICONS.ext" fill="currentColor"/></svg>
            <span class="tab-label">{{ item.label }}</span>
            <span
              v-if="tabCounts[item.name] !== undefined"
              class="tab-count"
              :class="{ 'is-empty': tabCounts[item.name] === 0 }"
            >{{ tabCounts[item.name] }}</span>
            <span v-if="index >= 3" class="tab-ext-tag">扩展</span>
          </div>
        </div>
      </div>

      <!-- 基础设置 -->
      <div v-show="tabsValue === '1'" class="tabPane">
        <!-- 基础配置卡片 -->
        <div class="base-settings-section">
          <div class="base-settings-content">
        <form-section title="基础信息" :summary="baseInfoSummary">
        <el-form-item label="节点编码：" prop="nodeCode">
          <el-input v-model="form.nodeCode" :disabled="disabled"></el-input>
        </el-form-item>
        <el-form-item label="节点名称：" prop="nodeName">
          <el-input v-model="form.nodeName" :disabled="disabled"></el-input>
        </el-form-item>
        <el-form-item label="默认变量：" class="default-variables-item">
          <node-variables v-model="form.nodeConfig" :disabled="disabled"></node-variables>
        </el-form-item>
        </form-section>

        <!-- 事件属性：中间节点选择事件类型后成为独立事件节点；固定开始 / 结束面板不含本区域。
             只开放白名单事件（定时 / 消息 / 错误 / 终止），Signal、Conditional、
             Escalation、Compensation、Cron 等不作为可选项。 -->
        <form-section title="事件" :summary="eventSectionSummary" ref="eventSection" :default-open="isEventNode">
        <el-form-item label="事件类型：" prop="eventType">
          <el-select v-model="form.eventType" placeholder="选择后成为事件节点" clearable :disabled="disabled" @change="handleEventTypeChange">
            <el-option-group v-for="group in eventTypeGroups" :key="group.label" :label="group.label">
              <el-option v-for="opt in group.options" :key="opt.value" :label="opt.label" :value="opt.value"/>
            </el-option-group>
          </el-select>
          <div class="field-hint">{{ eventTypeHint }}</div>
        </el-form-item>

        <template v-if="isEventNode">
          <el-form-item v-if="isMessageEvent" label="消息名称：" prop="eventName" :rules="rules.eventName">
            <el-input v-model="form.eventName" placeholder="外部定位该消息事件的消息名称" :disabled="disabled"></el-input>
          </el-form-item>
          <el-form-item v-if="isErrorEvent" label="错误码：" prop="eventName">
            <el-input v-model="form.eventName" placeholder="匹配指定错误码，留空捕获任意错误" :disabled="disabled"></el-input>
            <div class="field-hint">错误码留空表示通配，捕获任意错误</div>
          </el-form-item>

          <template v-if="isTimerEvent && eventCfg">
            <el-form-item label="定时类型：" prop="nodeConfig.event.timerType" :rules="rules.eventTimerType">
              <el-select v-model="form.nodeConfig.event.timerType" placeholder="请选择定时类型" :disabled="disabled" @change="handleTimerTypeChange">
                <el-option label="延迟时长（AFTER）" value="AFTER"/>
                <el-option label="固定间隔（FIXED）" value="FIXED"/>
                <el-option label="指定时间点（POINT_IN_TIME）" value="POINT_IN_TIME"/>
              </el-select>
            </el-form-item>

            <!-- 时长选择器：数值 + 单位生成 ISO-8601，覆盖 AFTER / FIXED 两种类型 -->
            <el-form-item v-if="timerIsDuration && !timerDurationAdvanced" label="时长："
              prop="nodeConfig.event.timerValue" :rules="rules.eventTimerValue">
              <div class="duration-picker">
                <el-input-number v-model="timerAmount" :min="1" :max="9999" :step="1" controls-position="right"
                  :disabled="disabled" @change="writeDurationValue"></el-input-number>
                <el-select v-model="timerUnit" :disabled="disabled" class="duration-unit" @change="writeDurationValue">
                  <el-option label="秒" value="S"/>
                  <el-option label="分钟" value="M"/>
                  <el-option label="小时" value="H"/>
                  <el-option label="天" value="D"/>
                </el-select>
                <el-link type="primary" :underline="false" class="advanced-link" :disabled="disabled"
                  @click="timerDurationAdvanced = true">高级</el-link>
              </div>
              <div class="field-hint">{{ timerValueHint }}</div>
            </el-form-item>

            <!-- 高级模式：直接编辑 ISO-8601 时长，支持 P1DT12H 等组合写法 -->
            <el-form-item v-if="timerIsDuration && timerDurationAdvanced" label="时间值："
              prop="nodeConfig.event.timerValue" :rules="rules.eventTimerValue">
              <div class="duration-picker">
                <el-input v-model="form.nodeConfig.event.timerValue" class="advanced-input"
                  :placeholder="timerValuePlaceholder" :disabled="disabled"></el-input>
                <el-link type="primary" :underline="false" class="advanced-link" :disabled="disabled"
                  @click="exitAdvancedIfSimple">返回选择器</el-link>
              </div>
              <div class="field-hint">{{ timerValueHint }}</div>
            </el-form-item>

            <!-- 时间点选择器：POINT_IN_TIME 每日多个固定时刻，最多 8 个 -->
            <el-form-item v-if="timerIsPointInTime" label="每日时间点："
              prop="nodeConfig.event.timerValue" :rules="rules.eventTimerValue">
              <div v-for="(_, index) in timerPoints" :key="index" class="time-point-row">
                <el-time-picker v-model="timerPoints[index]" value-format="HH:mm" format="HH:mm"
                  placeholder="选择时间" :disabled="disabled" @change="writePointValue"></el-time-picker>
                <el-button v-if="timerPoints.length > 1" link type="danger" :disabled="disabled" @click="removeTimePoint(index)">
                  <el-icon><Delete/></el-icon>
                </el-button>
              </div>
              <el-button link type="primary" :disabled="disabled || timerPoints.length >= 8" @click="addTimePoint">+ 添加时间点</el-button>
              <div class="field-hint">{{ timerValueHint }}</div>
            </el-form-item>

            <el-form-item label="时区：">
              <el-select v-model="form.nodeConfig.event.timezone" class="timezone-select" filterable allow-create clearable default-first-option
                placeholder="留空取引擎默认" :disabled="disabled">
                <el-option v-for="tz in COMMON_TIMEZONES" :key="tz" :label="tz" :value="tz"/>
              </el-select>
            </el-form-item>
            <el-form-item label="重复次数：">
              <el-input-number v-model="form.nodeConfig.event.maxCount" :min="1" :step="1" controls-position="right" :disabled="disabled"></el-input-number>
              <div class="field-hint">留空表示不限次数</div>
            </el-form-item>
          </template>

          <el-form-item v-if="isBoundaryEvent" label="附着任务节点：" prop="attachedToNodeCode" :rules="rules.attachedToNodeCode">
            <el-select v-model="form.attachedToNodeCode" placeholder="选择边界事件附着的任务节点" clearable :disabled="disabled">
              <el-option v-for="node in boundaryTaskNodes" :key="node.id" :label="node.text.value" :value="node.id"/>
            </el-select>
            <div class="field-hint">边界事件必须绑定到已有用户任务节点</div>
          </el-form-item>
          <el-form-item v-if="isBoundaryEvent" label="中断任务：" prop="interrupting" :rules="rules.interrupting">
            <el-switch v-model="form.interrupting" :active-value="true" :inactive-value="false" :disabled="disabled"></el-switch>
            <span class="field-hint">第一阶段仅支持中断型边界事件</span>
          </el-form-item>
        </template>
        </form-section>

        <form-section title="协作方式" :summary="collaborativeWaySummary">
        <!-- 协作方式 - 卡片式单选 -->
        <el-form-item label="协作方式：" prop="collaborativeWay">
          <div class="radio-card-group">
            <label
              class="radio-card-item"
              :class="{ 'is-checked': form.collaborativeWay === '1' }"
              @click="!disabled && (form.collaborativeWay = '1')"
              v-if="form.collaborativeWay === '1' || showWays"
            >
              <span class="radio-card-dot"></span>
              <span class="radio-card-text">或签</span>
              <el-tooltip effect="dark" placement="top" content="只需一个人审批">
                <el-icon :size="13" class="radio-card-tip"><WarningFilled /></el-icon>
              </el-tooltip>
            </label>
            <label
              class="radio-card-item"
              :class="{ 'is-checked': form.collaborativeWay === '2' }"
              @click="!disabled && (form.collaborativeWay = '2')"
              v-if="form.collaborativeWay === '2' || showWays"
            >
              <span class="radio-card-dot"></span>
              <span class="radio-card-text">票签</span>
              <el-tooltip effect="dark" placement="top" content="部分办理人审批，建议选择用户；如果选择角色或者部门等，需自行通过办理人表达式或者监听器，转成具体办理用户">
                <el-icon :size="13" class="radio-card-tip"><WarningFilled /></el-icon>
              </el-tooltip>
            </label>
            <label
              class="radio-card-item"
              :class="{ 'is-checked': form.collaborativeWay === '3' }"
              @click="!disabled && (form.collaborativeWay = '3')"
              v-if="form.collaborativeWay === '3' || showWays"
            >
              <span class="radio-card-dot"></span>
              <span class="radio-card-text">会签</span>
              <el-tooltip effect="dark" placement="top" content="所有办理都需要审批，建议选择用户；如果选择角色或者部门等，需自行通过办理人表达式或者监听器，转成具体办理用户">
                <el-icon :size="13" class="radio-card-tip"><WarningFilled /></el-icon>
              </el-tooltip>
            </label>
          </div>
        </el-form-item>

        <el-form-item label="票签策略：" prop="nodeRatio" v-if="form.collaborativeWay === '2'" class="ratio-item">
          <div class="ratio-row">
            <el-select v-model="form.nodeRatioType" class="ratio-type" placeholder="策略类型" clearable @clear="handleClear">
                <el-option label="通过率" value="passRatio"/>
                <el-option label="固定通过人数" value="passCount"/>
                <el-option label="固定驳回人数" value="rejectCount"/>
                <el-option label="默认表达式" value="default" v-if="framework ==='SPRING_BOOT'"/>
                <el-option label="spel表达式" value="spel" v-if="framework ==='SPRING_BOOT'"/>
            </el-select>
            <el-input v-model="form.nodeRatioValue" class="ratio-value" :placeholder="getNodeRatioDescription()"/>
          </div>
        </el-form-item>
        <el-form-item label="驳回节点：" prop="formCustom" class="reject-item" :required="form.collaborativeWay === '2'">
          <el-select v-model="form.anyNodeSkip" placeholder="请选择驳回到的节点" clearable>
            <el-option
                v-for="dict in filteredNodes"
                :key="dict.id"
                :label="dict.text.value"
                :value="dict.id"
            />
          </el-select>
          <div v-if="form.collaborativeWay === '2'" class="field-hint">票签必须指定驳回节点</div>
          <div v-else class="field-hint">驳回目标以这里为准：配了就退到该节点，否则沿当前节点的「退回线」退回</div>
        </el-form-item>
        </form-section>

        <!-- 抄送：流程到达该节点时把这条待办抄送给这些人。
             只写留痕（flow_his_task + flow_user，类型 4），不产生待办、不改流程状态，也不走协作方式。
             只有会产生待办的节点才有抄送——事件节点（含边界事件，画布上是圆圈）不产生待办，不开放该配置。 -->
        <form-section v-if="!isEventNode" title="抄送" :summary="copySummary" :default-open="false">
        <el-form-item label="抄送人：">
          <div class="copy-picker">
            <div class="copy-tags" v-if="copyHandlerRows.length > 0">
              <el-tag
                v-for="(row, index) in copyHandlerRows"
                :key="row.storageId"
                class="copy-tag"
                size="small"
                :closable="!disabled"
                @close="removeCopyHandler(index)"
              >{{ row.handlerName || row.storageId }}</el-tag>
            </div>
            <div v-else class="copy-empty">{{ disabled ? '未配置抄送人' : '到达该节点时自动抄送给这些人' }}</div>
            <div class="copy-actions" v-if="!disabled">
              <el-button size="small" class="add-row-btn add-row-btn-secondary" @click="openCopyUser">选择</el-button>
              <el-button size="small" class="add-row-btn add-row-btn-secondary" @click="addCopyInitiator">发起人</el-button>
            </div>
          </div>
          <div class="field-hint">抄送只产生留痕，不进入待办；同一节点配置多人时每人各收一条。选部门 / 角色时由后端展开成具体用户，展开不到人的标识不会抄送</div>
        </el-form-item>
        </form-section>

        <form-section title="表单" :summary="formSummary" :default-open="false">
        <!-- 自定义表单 - 卡片式单选 -->
        <el-form-item label="自定义表单：" prop="formCustom">
          <div class="radio-card-group radio-card-sm">
            <label
              class="radio-card-item"
              :class="{ 'is-checked': form.formCustom === 'N' }"
              @click="!disabled && (form.formCustom = 'N')"
            >
              <span class="radio-card-dot"></span>
              <span class="radio-card-text">否</span>
              <el-tooltip effect="dark" placement="top" content="填写页面地址：如system/process/approve">
                <el-icon :size="13" class="radio-card-tip"><WarningFilled /></el-icon>
              </el-tooltip>
            </label>
            <label
              class="radio-card-item"
              :class="{ 'is-checked': form.formCustom === 'Y' }"
              @click="!disabled && (form.formCustom = 'Y')"
            >
              <span class="radio-card-dot"></span>
              <span class="radio-card-text">是</span>
              <el-tooltip effect="dark" placement="top" content="填写自定义表单的唯一标识：如formCode+version">
                <el-icon :size="13" class="radio-card-tip"><WarningFilled /></el-icon>
              </el-tooltip>
            </label>
          </div>
        </el-form-item>

        <el-form-item label="表单路径：" prop="formPath" v-if="form.formCustom === 'N'">
          <el-input v-model="form.formPath"></el-input>
        </el-form-item>
        <el-form-item label="表单唯一标识：" prop="formPath" v-else-if="form.formCustom === 'Y'">
            <el-tree-select
                v-model="form.formPath"
                :data="formPathList"
                :props="{ value: 'id', label: 'name', children: 'children' }"
                value-key="id"
                placeholder="请选择流程类别"
                check-strictly/>
        </el-form-item>
        </form-section>
        <form-section title="节点监听器" :summary="listenerSummary" ref="listenerSection" :default-open="false">
        <div class="section-card section-purple">
          <div class="section-card-body">
            <listener-config :rows="form.listenerRows" :disabled="disabled" />
          </div>
        </div>
        </form-section>

        <!-- 扩展属性（KV + 说明，存 nodeConfig 顶层键；variables 等为引擎保留键） -->
        <form-section title="扩展属性" :summary="kvExtSummary" :default-open="false">
          <div class="section-card section-blue">
            <div class="section-card-body">
              <el-table :data="kvRows" class="kv-table" style="width: 100%" empty-text="暂无扩展属性">
                <el-table-column prop="code" :width="isMobile ? 64 : 112" label="属性名">
                  <template #default="scope">
                    <el-input v-model="scope.row.code" :disabled="disabled" placeholder="请输入属性名" maxlength="50" @input="syncNodeConfig" />
                  </template>
                </el-table-column>
                <el-table-column prop="value" label="属性值" :min-width="isMobile ? 80 : 120">
                  <template #default="scope">
                    <el-input v-model="scope.row.value" :disabled="disabled" placeholder="请输入属性值" maxlength="500" @input="syncNodeConfig" />
                  </template>
                </el-table-column>
                <el-table-column prop="description" label="说明" :min-width="isMobile ? 90 : 120">
                  <template #default="scope">
                    <el-input v-model="scope.row.description" :disabled="disabled" placeholder="补充说明（选填）" maxlength="200" @input="syncNodeConfig" />
                  </template>
                </el-table-column>
                <el-table-column label="操作" :width="isMobile ? 38 : 56" align="center" v-if="!disabled">
                  <template #default="scope">
                    <el-button link size="small" type="danger" :icon="Delete" aria-label="删除扩展属性"
                               @click="handleDeleteKvRow(scope.$index)" />
                  </template>
                </el-table-column>
              </el-table>
              <div class="action-buttons">
                <el-button v-if="!disabled" size="small" class="add-row-btn" @click="handleAddKvRow">增加行</el-button>
              </div>
            </div>
          </div>
        </form-section>

          </div>
        </div>

        <!-- 节点扩展属性（基础设置中的）- 可折叠 -->
        <div v-if="baseList.length > 0" class="ext-attributes-section ext-primary" :class="{ 'is-collapsed': baseExtCollapsed }">
          <div class="ext-attributes-header ext-header-clickable" @click="baseExtCollapsed = !baseExtCollapsed">
            <span class="ext-attributes-icon ext-icon-star">
              <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z" fill="currentColor"/>
              </svg>
            </span>
            <span class="ext-attributes-title">节点扩展属性</span>
            <span class="ext-attributes-badge" :class="{ 'is-empty': baseFilled === 0 }">{{ baseFilled }}/{{ baseList.length }} 已填</span>
            <span v-if="baseExtCollapsed" class="ext-collapse-summary">{{ baseExtSummary }}</span>
            <span class="ext-collapse-arrow">
              <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z" :class="{ 'arrow-rotated': !baseExtCollapsed }" fill="currentColor"/>
              </svg>
            </span>
          </div>
          <transition name="ext-collapse">
            <div class="ext-attributes-content" v-show="!baseExtCollapsed">
              <nodeExtList ref="nodeBase" v-model="form.ext" :formList="baseList" :disabled="disabled"></nodeExtList>
            </div>
          </transition>
        </div>
      </div>

      <!-- 办理人设置 -->
      <div v-show="tabsValue === '2'" class="tabPane tabPane-full">
        <div class="section-card section-blue">
          <div class="section-card-body">
              <el-table :data="permissionRows" style="width: 100%;" class="inputGroup handler-table-mobile"
                  :table-layout="isMobile ? 'fixed' : 'auto'"
              >
                  <el-table-column prop="storageId" label="入库主键" :width="isMobile ? undefined : 250">
                      <template #default="scope">
                          <el-form-item prop="storageId">
                              <el-input v-model="scope.row.storageId" style="width: 100%;" @blur="event => inputBlur(event, scope.$index)"></el-input>
                          </el-form-item>
                      </template>
                  </el-table-column>
                  <el-table-column prop="handlerName" label="权限名称"></el-table-column>
                  <el-table-column label="操作" :width="isMobile ? undefined : 65" align="center" v-if="!disabled">
                      <template #default="scope">
                          <el-button link size="small" type="danger" v-if="!disabled" :icon="Delete" @click="delPermission(scope.$index)"/>
                      </template>
                  </el-table-column>
              </el-table>
              <div class="action-buttons">
                <el-button v-if="!disabled" size="small" class="add-row-btn" @click="addPermission">添加行</el-button>
                <el-button v-if="!disabled" size="small" class="add-row-btn add-row-btn-secondary" @click="initUser">选择</el-button>
                <el-button v-if="!disabled" size="small" class="add-row-btn add-row-btn-secondary" @click="addInitiator">发起人</el-button>
              </div>
          </div>
        </div>
      </div>


      <!-- 动态页签（按钮权限等）- 都是节点扩展属性 -->
      <div v-show="tabsValue !== '1' && tabsValue !== '2'" class="tabPane tabPane-full">
        <div v-if="buttonList[tabsValue] && buttonList[tabsValue].length > 0" class="ext-attributes-section ext-secondary">
          <div class="ext-attributes-header ext-secondary-header">
            <span class="ext-attributes-icon ext-icon-puzzle">
              <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M20 6H16V4C16 2.89 15.11 2 14 2H10C8.89 2 8 2.89 8 4V6H4C2.89 6 2.01 6.89 2.01 8L2 19C2 20.11 2.89 21 4 21H20C21.11 21 22 20.11 22 19V8C22 6.89 21.11 6 20 6ZM10 4H14V6H10V4ZM12 17H6V15H12V17ZM18 13H6V11H18V13Z" fill="currentColor"/>
              </svg>
            </span>
            <span class="ext-attributes-title ext-secondary-title">{{ getCurrentTabLabel() }}</span>
            <span class="ext-attributes-badge ext-secondary-badge">节点扩展属性 · {{ countFilled(buttonList[tabsValue]) }}/{{ buttonList[tabsValue].length }} 已填</span>
          </div>
          <div class="ext-attributes-content">
            <nodeExtList :ref="`nodeExtList_${tabsValue}`" v-model="form.ext" :formList="buttonList[tabsValue]" :disabled="disabled"></nodeExtList>
          </div>
        </div>
      </div>
    </el-form>

    <!-- 权限标识：会签票签选择用户 -->
    <el-dialog title="人员选择" v-if="userVisible" v-model="userVisible" :width="isMobile ? '96%' : '80%'" append-to-body
      class="person-select-dialog" :class="{ 'mobile-user-dialog': isMobile }"
    >
      <selectUser v-model:selectUser="form.permissionFlag" v-model:userVisible="userVisible" :permissionRows="permissionRows" @handleUserSelect="handleUserSelect"></selectUser>
    </el-dialog>

    <!-- 抄送人：复用同一套人员选择器，结果写 node_config.copy.handlers -->
    <el-dialog title="选择抄送人" v-if="copyUserVisible" v-model="copyUserVisible" :width="isMobile ? '96%' : '80%'" append-to-body
      class="person-select-dialog" :class="{ 'mobile-user-dialog': isMobile }"
    >
      <selectUser :selectUser="copyHandlerIds" v-model:userVisible="copyUserVisible" :permissionRows="copyHandlerRows" @handleUserSelect="handleCopyUserSelect"></selectUser>
    </el-dialog>
  </div>
</template>

<script setup name="Between">
import selectUser from "./selectUser";
import { Delete } from '@element-plus/icons-vue'
import {publishedList, handlerDict, nodeExt, handlerFeedback} from "@/api/flow/definition";
import nodeExtList from "./nodeExtList";
import formSection from "./formSection";
import listenerConfig from "./listenerConfig";
import nodeVariables from "./nodeVariables";
import {getPreviousNodes} from "@/components/design/common/js/tool.js";
import {getFramework} from "@/utils/auth.js";
const { proxy } = getCurrentInstance();

const props = defineProps({
  modelValue: {
    type: Object,
    default () {
      return {}
    }
  },
  disabled: { // 是否禁止
    type: Boolean,
    default: false
  },
  // 是否展示所有协作方式
  showWays: {
    type: Boolean,
    default: true
  },
  nodes: {
    type: Array,
    default () {
      return []
    }
  },
  skips: {
    type: Array,
    default () {
      return []
    }
  },
  formPathList: {
      type: Array,
      default () {
          return []
      }
  },
});

const tabsValue = ref("1");
// 节点扩展属性折叠状态（默认收起）
const baseExtCollapsed = ref(true);
// 扩展属性行（KV，存 flow_node.node_config 顶层键）
const kvRows = ref([]);
// Tab 图标（单路径 SVG，viewBox 0 0 24 24，跟随 tab 文字色 currentColor）
const TAB_ICONS = {
  base: 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z',
  handler: 'M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5s-3 1.34-3 3 1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z',
  ext: 'M20.5 11H19V7c0-1.1-.9-2-2-2h-4V3.5C13 2.12 11.88 1 10.5 1S8 2.12 8 3.5V5H4c-1.1 0-1.99.9-1.99 2v3.8H3.5c1.49 0 2.7 1.21 2.7 2.7s-1.21 2.7-2.7 2.7H2V20c0 1.1.9 2 2 2h3.8v-1.5c0-1.49 1.21-2.7 2.7-2.7 1.49 0 2.7 1.21 2.7 2.7V22H17c1.1 0 2-.9 2-2v-4h1.5c1.38 0 2.5-1.12 2.5-2.5S21.88 11 20.5 11z',
};
const tabsList = ref([
  { label: "基础设置", name: "1", iconPath: TAB_ICONS.base },
  { label: "办理人设置", name: "2", iconPath: TAB_ICONS.handler },
]);
const form = ref(props.modelValue);
const userVisible = ref(false);
const framework = getFramework()
//基础设置扩展属性
const baseList = ref([]);
//按钮权限
const buttonList = ref({});
const definitionList = ref([]); // 流程表单列表
const dictList = ref(); // 办理人选项
const permissionRows = ref([]); // 办理人表格
const emit = defineEmits(['update:modelValue']);

/** 扩展属性值是否算"已填写"：多选看数组长度，其余看去掉空白后是否有内容 */
function isFilled(value) {
  if (Array.isArray(value)) return value.length > 0;
  return value !== null && value !== undefined && String(value).trim() !== '';
}

/** 一组扩展属性字段里已填写的数量 */
function countFilled(fieldList) {
  const ext = form.value.ext || {};
  return (fieldList || []).filter((item) => isFilled(ext[item.code])).length;
}

const baseFilled = computed(() => countFilled(baseList.value));

// 页签计数：只给「有条目语义」的分组加。基础设置是一组表单字段，没有数量含义，所以不加。
// 监听器条数：填了类路径的行才算一条配置
const listenerCount = computed(() => (form.value.listenerRows || []).filter((row) => isFilled(row.listenerPath)).length);

const tabCounts = computed(() => {
  const counts = {
    '2': permissionRows.value.length,
  };
  Object.keys(buttonList.value).forEach((code) => {
    counts[code] = countFilled(buttonList.value[code]);
  });
  return counts;
});

// 收起状态下给一行摘要，避免"收起来就完全看不见内容"
const baseExtSummary = computed(() => {
  const ext = form.value.ext || {};
  const filled = baseList.value.filter((item) => isFilled(ext[item.code]));
  if (filled.length === 0) return '未填写';
  const first = filled[0];
  const text = `${first.label}：${ext[first.code]}`;
  return filled.length > 1 ? `${text}　等 ${filled.length} 项` : text;
});

/** 扩展属性说明的元数据键：与属性值分开存，保持 nodeConfig[code] 仍是原始标量值 */
const EXT_META_KEY = '__extMeta';

/** 引擎保留的节点配置键：默认变量、服务任务、事件、抄送与说明元数据，不参与自定义 KV 行 */
const NODE_CONFIG_RESERVED_KEYS = ['variables', '__variableMeta', 'serviceTask', 'event', 'copy', EXT_META_KEY];

/** 最近一次与表格同步的 nodeConfig 序列化结果，用于区分外部回显与自身写回 */
let lastNodeConfigSnapshot = '';

/** 读取扩展属性说明元数据，兼容缺失、非对象与旧数据 */
function getExtMeta(config) {
  const meta = config && config[EXT_META_KEY];
  return meta && typeof meta === 'object' && !Array.isArray(meta) ? meta : {};
}

/** 初始化 KV 行：从 form.nodeConfig 读取非保留键 */
function initKvRows() {
  const config = form.value.nodeConfig && typeof form.value.nodeConfig === 'object'
    ? form.value.nodeConfig : {};
  const extMeta = getExtMeta(config);
  kvRows.value = Object.keys(config)
    .filter(code => !NODE_CONFIG_RESERVED_KEYS.includes(code))
    .map(code => ({
      code,
      value: config[code],
      description: extMeta[code] === undefined || extMeta[code] === null ? '' : String(extMeta[code])
    }));
  lastNodeConfigSnapshot = JSON.stringify(config);
}

/** KV 行变化时重建 nodeConfig：保留引擎配置，剔除空属性名，并同步说明元数据 */
function syncNodeConfig() {
  const previous = form.value.nodeConfig && typeof form.value.nodeConfig === 'object'
    ? form.value.nodeConfig : {};
  const config = {};
  const extMeta = {};
  kvRows.value.forEach(row => {
    const code = String(row.code || '').trim();
    if (code && !NODE_CONFIG_RESERVED_KEYS.includes(code)) {
      config[code] = row.value;
      const description = String(row.description || '').trim();
      if (description) {
        extMeta[code] = description;
      }
    }
  });
  // 这些段由其它面板维护，编辑扩展属性时只透传，避免互相覆盖
  ['variables', '__variableMeta', 'serviceTask', 'event', 'copy'].forEach(key => {
    if (previous[key] !== undefined) {
      config[key] = previous[key];
    }
  });
  if (Object.keys(extMeta).length > 0) {
    config[EXT_META_KEY] = extMeta;
  }
  form.value.nodeConfig = config;
  lastNodeConfigSnapshot = JSON.stringify(config);
}

// 增加扩展属性行
function handleAddKvRow() {
  kvRows.value.push({ code: "", value: "", description: "" });
}

// 删除扩展属性行
function handleDeleteKvRow(index) {
  kvRows.value.splice(index, 1);
  syncNodeConfig();
}

// 收起状态下的摘要（配合 form-section 折叠展示）
const kvExtSummary = computed(() => {
  const filled = kvRows.value.filter(row => (row.code || '').trim() !== '');
  if (filled.length === 0) return '未填写';
  const first = filled[0];
  return filled.length > 1 ? `${first.code}：${first.value}　等 ${filled.length} 项` : `${first.code}：${first.value}`;
});

// 基础设置三个分组的收起摘要
const COLLABORATIVE_WAY_LABELS = {'1': '或签', '2': '票签', '3': '会签'};

const baseInfoSummary = computed(() => {
  const name = form.value.nodeName;
  if (!name) return '未命名节点';
  return form.value.nodeCode ? `${name}（${form.value.nodeCode}）` : name;
});

const collaborativeWaySummary = computed(() => {
  const way = COLLABORATIVE_WAY_LABELS[form.value.collaborativeWay] || '未设置';
  return form.value.collaborativeWay === '2' && form.value.nodeRatio ? `${way} · ${form.value.nodeRatio}` : way;
});

const formSummary = computed(() => {
  if (form.value.formCustom === 'Y') return form.value.formPath ? `自定义表单 · ${form.value.formPath}` : '自定义表单';
  return form.value.formPath ? `页面地址 · ${form.value.formPath}` : '未配置表单';
});

const listenerSummary = computed(() => listenerCount.value > 0 ? `已配置 ${listenerCount.value} 项` : '未配置监听器');

// ========== 事件节点（第一阶段白名单） ==========

/** 可选事件类型分组：仅第一阶段白名单，不提供 Signal / Conditional / Error / Cron 等 */
const EVENT_TYPE_GROUPS = [
  { label: '启动事件', options: [
    { value: 'TIMER_START', label: '定时启动' },
    { value: 'MESSAGE_START', label: '消息启动' },
  ]},
  { label: '中间捕获事件', options: [
    { value: 'TIMER_CATCH', label: '定时捕获' },
    { value: 'MESSAGE_CATCH', label: '消息捕获' },
    { value: 'ERROR_CATCH', label: '错误捕获' },
  ]},
  { label: '中间抛出事件', options: [
    { value: 'MESSAGE_THROW', label: '消息抛出' },
  ]},
  { label: '边界事件', options: [
    { value: 'TIMER_BOUNDARY', label: '定时边界' },
    { value: 'MESSAGE_BOUNDARY', label: '消息边界' },
    { value: 'ERROR_BOUNDARY', label: '错误边界' },
  ]},
  { label: '结束事件', options: [
    { value: 'TERMINATE_END', label: '终止结束' },
    { value: 'MESSAGE_END', label: '消息结束' },
  ]},
];

/** 事件类型显示名（与后端 EventType 枚举一致） */
const EVENT_TYPE_LABELS = {
  TIMER_START: '定时启动', MESSAGE_START: '消息启动',
  TIMER_CATCH: '定时捕获', MESSAGE_CATCH: '消息捕获', MESSAGE_THROW: '消息抛出',
  TIMER_BOUNDARY: '定时边界', MESSAGE_BOUNDARY: '消息边界',
  ERROR_CATCH: '错误捕获', ERROR_BOUNDARY: '错误边界',
  TERMINATE_END: '终止结束', MESSAGE_END: '消息结束',
};

const TIMER_TYPE_LABELS = { AFTER: '延迟时长', FIXED: '固定间隔', POINT_IN_TIME: '指定时间点' };

/** 是否紧邻固定开始节点（存在来自 start 节点的普通连线）——启动事件仅允许该位置 */
function adjacentToStartNode() {
  const startIds = (props.nodes || []).filter(node => node.type === 'start').map(node => node.id);
  return (props.skips || []).some(skip => skip.properties && skip.properties.skipType === 'PASS'
    && skip.targetNodeId === form.value.nodeCode && startIds.includes(skip.sourceNodeId));
}

/** 是否直连固定结束节点（存在指向 end 节点的普通连线）——结束事件仅允许该位置 */
function connectedToEndNode() {
  const endIds = (props.nodes || []).filter(node => node.type === 'end').map(node => node.id);
  return (props.skips || []).some(skip => skip.properties && skip.properties.skipType === 'PASS'
    && skip.sourceNodeId === form.value.nodeCode && endIds.includes(skip.targetNodeId));
}

/** 类型下拉按当前事件类别锁定：同类事件之间才能切换，避免出现拓扑不合法的选项 */
const eventTypeGroups = computed(() => {
  const current = form.value.eventType;
  if (['TIMER_BOUNDARY', 'MESSAGE_BOUNDARY', 'ERROR_BOUNDARY'].includes(current)) {
    return EVENT_TYPE_GROUPS.filter(group => group.label === '边界事件');
  }
  if (['TIMER_START', 'MESSAGE_START'].includes(current)) {
    return EVENT_TYPE_GROUPS.filter(group => group.label === '启动事件');
  }
  if (['TIMER_CATCH', 'MESSAGE_CATCH', 'ERROR_CATCH'].includes(current)) {
    return EVENT_TYPE_GROUPS.filter(group => group.label === '中间捕获事件');
  }
  if (['MESSAGE_THROW'].includes(current)) {
    return EVENT_TYPE_GROUPS.filter(group => group.label === '中间抛出事件');
  }
  if (['TERMINATE_END', 'MESSAGE_END'].includes(current)) {
    return EVENT_TYPE_GROUPS.filter(group => group.label === '结束事件');
  }
  // 未选择类型：捕获 / 抛出任意位置可用；启动事件需紧邻开始节点；结束事件需直连结束节点；边界只能从侧栏拖入
  const allowedLabels = ['中间捕获事件', '中间抛出事件'];
  if (adjacentToStartNode()) allowedLabels.push('启动事件');
  if (connectedToEndNode()) allowedLabels.push('结束事件');
  return EVENT_TYPE_GROUPS.filter(group => allowedLabels.includes(group.label));
});

const eventTypeHint = computed(() => {
  if (['TIMER_BOUNDARY', 'MESSAGE_BOUNDARY', 'ERROR_BOUNDARY'].includes(form.value.eventType)) {
    return '边界事件附着在任务节点上触发，可在三种边界类型间切换';
  }
  if (['TIMER_CATCH', 'MESSAGE_CATCH', 'ERROR_CATCH'].includes(form.value.eventType)) {
    return '流程在此等待触发条件满足后继续，可在三种捕获类型间切换';
  }
  if (['TIMER_START', 'MESSAGE_START'].includes(form.value.eventType)) {
    return '启动事件连接在固定开始节点之后，可在两种启动类型间切换';
  }
  if (['TERMINATE_END', 'MESSAGE_END'].includes(form.value.eventType)) {
    return '结束事件连接在固定结束节点之前，可在两种结束类型间切换';
  }
  if (attachedBoundaryCount.value > 0) {
    return `已被 ${attachedBoundaryCount.value} 个边界事件附着，转为事件节点会使附着失效，请在边界事件面板更换附着目标`;
  }
  if (adjacentToStartNode() && connectedToEndNode()) {
    return '可选捕获 / 抛出事件；当前节点紧邻开始且直连结束，也可转为启动或结束事件';
  }
  if (adjacentToStartNode()) {
    return '可选捕获 / 抛出事件；当前节点紧邻开始节点，也可转为启动事件';
  }
  if (connectedToEndNode()) {
    return '可选捕获 / 抛出事件；当前节点直连结束节点，也可转为结束事件';
  }
  return '可选捕获 / 抛出事件；启动事件需紧邻开始节点，结束事件需直连结束节点，边界事件请拖入「边界事件」节点';
});

const isEventNode = computed(() => !!form.value.eventType);
const isStartEvent = computed(() => ['TIMER_START', 'MESSAGE_START'].includes(form.value.eventType));
const isTimerEvent = computed(() => ['TIMER_START', 'TIMER_CATCH', 'TIMER_BOUNDARY'].includes(form.value.eventType));
const isMessageEvent = computed(() => ['MESSAGE_START', 'MESSAGE_CATCH', 'MESSAGE_THROW', 'MESSAGE_BOUNDARY', 'MESSAGE_END']
  .includes(form.value.eventType));
const isBoundaryEvent = computed(() => ['TIMER_BOUNDARY', 'MESSAGE_BOUNDARY', 'ERROR_BOUNDARY'].includes(form.value.eventType));
const isErrorEvent = computed(() => ['ERROR_CATCH', 'ERROR_BOUNDARY'].includes(form.value.eventType));

/** node_config.event 段（定时事件专用；消息 / 边界事件不写该段） */
const eventCfg = computed(() => {
  const config = form.value.nodeConfig;
  return config && typeof config === 'object' && config.event && typeof config.event === 'object'
    ? config.event : null;
});

/** 边界事件可附着的任务节点：已有中间节点中排除当前节点与其它事件节点 */
const boundaryTaskNodes = computed(() => (props.nodes || []).filter(node => node.type === 'between'
  && node.id !== form.value.nodeCode && !(node.properties && node.properties.eventType)));

/** 附着在当前节点上的边界事件数量（其它边界节点选了本节点作为附着目标） */
const attachedBoundaryCount = computed(() => (props.nodes || []).filter(node => node.type === 'between'
  && node.id !== form.value.nodeCode
  && ['TIMER_BOUNDARY', 'MESSAGE_BOUNDARY', 'ERROR_BOUNDARY'].includes(node.properties && node.properties.eventType)
  && node.properties.attachedToNodeCode === form.value.nodeCode).length);

const eventSectionSummary = computed(() => {
  if (!isEventNode.value) {
    return attachedBoundaryCount.value > 0
      ? `普通中间节点 · 已被 ${attachedBoundaryCount.value} 个边界事件附着`
      : '普通中间节点';
  }
  const label = EVENT_TYPE_LABELS[form.value.eventType] || form.value.eventType;
  if (isTimerEvent.value && eventCfg.value) {
    const timerLabel = TIMER_TYPE_LABELS[eventCfg.value.timerType] || '未配置定时';
    const friendly = friendlyTimerValue();
    return friendly ? `${label} · ${timerLabel} ${friendly}` : `${label} · ${timerLabel}`;
  }
  if (isMessageEvent.value) return form.value.eventName ? `${label} · ${form.value.eventName}` : `${label} · 未配置名称`;
  if (isErrorEvent.value) return form.value.eventName ? `${label} · ${form.value.eventName}` : `${label} · 通配`;
  return label;
});

/** 定时值人性化展示：单单位时长 / 时间点数组转中文，其余原样 */
function friendlyTimerValue() {
  const value = String((eventCfg.value && eventCfg.value.timerValue) || '').trim();
  if (!value) return '';
  const match = value.match(SIMPLE_DURATION_PATTERN);
  if (match) {
    const unitText = { S: '秒', M: '分钟', H: '小时', D: '天' };
    const amount = match[1] || match[2] || match[3] || match[4];
    const unit = match[1] != null ? 'D' : (match[2] != null ? 'H' : (match[3] != null ? 'M' : 'S'));
    return `${amount} ${unitText[unit]}`;
  }
  const points = parseTimePoints(value);
  if (points.length) return points.join('、');
  return value;
}

const timerValuePlaceholder = computed(() => eventCfg.value && eventCfg.value.timerType === 'POINT_IN_TIME'
  ? '如 ["09:00","18:00"]' : '如 P3D（3 天）、PT30M（30 分钟）');

const timerValueHint = computed(() => {
  const type = eventCfg.value && eventCfg.value.timerType;
  if (type === 'AFTER' || type === 'FIXED') {
    // 高级模式下且当前值不是单一单位时长：说明自动降级的原因
    const value = String((eventCfg.value && eventCfg.value.timerValue) || '').trim();
    if (timerDurationAdvanced.value && value && !SIMPLE_DURATION_PATTERN.test(value)) {
      return '当前值为组合或特殊写法（如 P1DT12H），已自动切换高级模式；改成单一单位后可点「返回选择器」';
    }
    return type === 'AFTER' ? '进入节点后开始计时，到点触发' : '按固定间隔重复触发';
  }
  if (type === 'POINT_IN_TIME') return '每日在所选时刻触发，可设置多个时间点';
  return '';
});

// ========== 定时触发选择器：数值 + 单位 / 时间点列表，与 ISO-8601 持久化值双向转换 ==========

/** 常用时区候选（可搜索可自定义），避免用户手写 IANA 名称出错 */
const COMMON_TIMEZONES = [
  'Asia/Shanghai', 'UTC', 'Asia/Tokyo', 'Asia/Singapore', 'Asia/Hong_Kong',
  'Europe/London', 'Europe/Berlin', 'America/New_York', 'America/Los_Angeles',
];

const timerIsDuration = computed(() => ['AFTER', 'FIXED'].includes(eventCfg.value && eventCfg.value.timerType));
const timerIsPointInTime = computed(() => eventCfg.value && eventCfg.value.timerType === 'POINT_IN_TIME');

/** 时长选择器状态：单单位数值（分钟 / 小时 / 天）；组合值走高级模式手写 */
const timerAmount = ref(1);
const timerUnit = ref('D');
const timerDurationAdvanced = ref(false);

/** 时间点选择器状态：HH:mm 字符串数组 */
const timerPoints = ref(['09:00']);

/** 单单位 ISO-8601 时长（Java Duration.parse 不支持周，故只提供秒 / 分钟 / 小时 / 天） */
const SIMPLE_DURATION_PATTERN = /^(?:P(\d+)D|PT(\d+)H|PT(\d+)M|PT(\d+)S)$/;

function syncDurationFromValue() {
  const value = (eventCfg.value && eventCfg.value.timerValue) || '';
  const match = String(value).trim().match(SIMPLE_DURATION_PATTERN);
  if (match) {
    if (match[1] != null) { timerAmount.value = Number(match[1]); timerUnit.value = 'D'; }
    else if (match[2] != null) { timerAmount.value = Number(match[2]); timerUnit.value = 'H'; }
    else if (match[3] != null) { timerAmount.value = Number(match[3]); timerUnit.value = 'M'; }
    else { timerAmount.value = Number(match[4]); timerUnit.value = 'S'; }
    timerDurationAdvanced.value = false;
  } else if (!value) {
    timerAmount.value = 1;
    timerUnit.value = 'D';
    timerDurationAdvanced.value = false;
  } else {
    // P1DT12H 等组合写法或非法值：交给高级模式，保留原值供修改
    timerDurationAdvanced.value = true;
  }
}

/** 选择器 → 持久化值：PT30S / PT30M / PT2H / P1D */
function writeDurationValue() {
  if (props.disabled || !eventCfg.value) return;
  const amount = Math.max(1, Math.floor(Number(timerAmount.value) || 1));
  form.value.nodeConfig.event.timerValue = timerUnit.value === 'D'
    ? `P${amount}D` : `PT${amount}${timerUnit.value}`;
}

function exitAdvancedIfSimple() {
  if (String((eventCfg.value && eventCfg.value.timerValue) || '').match(SIMPLE_DURATION_PATTERN)) {
    timerDurationAdvanced.value = false;
  }
}

const TIME_POINT_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;

function parseTimePoints(value) {
  const raw = String(value || '').trim();
  if (!raw) return [];
  let items = raw;
  if (items.startsWith('[')) {
    try { items = JSON.parse(items); } catch (e) { return []; }
  } else {
    items = items.split(',');
  }
  return (Array.isArray(items) ? items : [])
    .map(item => String(item || '').trim()).filter(item => TIME_POINT_PATTERN.test(item));
}

function syncPointsFromValue() {
  const points = parseTimePoints(eventCfg.value && eventCfg.value.timerValue);
  timerPoints.value = points.length ? [...points] : ['09:00'];
}

/** 时间点列表 → JSON 数组持久化；全部无效时置空由校验兜底 */
function writePointValue() {
  if (props.disabled || !eventCfg.value) return;
  const points = timerPoints.value.filter(point => TIME_POINT_PATTERN.test(String(point || '')));
  form.value.nodeConfig.event.timerValue = points.length ? JSON.stringify(points) : '';
}

function addTimePoint() {
  timerPoints.value.push('09:00');
  writePointValue();
}

function removeTimePoint(index) {
  timerPoints.value.splice(index, 1);
  writePointValue();
}

/** 定时类型切换：按新类型同步选择器初始状态，缺省值时补 P1D / ["09:00"] */
function handleTimerTypeChange() {
  if (props.disabled || !eventCfg.value) return;
  const value = String(eventCfg.value.timerValue || '').trim();
  if (timerIsPointInTime.value) {
    if (!parseTimePoints(value).length) {
      form.value.nodeConfig.event.timerValue = '["09:00"]';
    }
    syncPointsFromValue();
  } else {
    if (!SIMPLE_DURATION_PATTERN.test(value)) {
      form.value.nodeConfig.event.timerValue = 'P1D';
    }
    syncDurationFromValue();
  }
}

// 外部值变化（节点切换 / 回显 / 代码写入）时同步选择器状态
watch(() => {
  const cfg = eventCfg.value;
  return cfg ? `${cfg.timerType || ''}|${cfg.timerValue || ''}` : '';
}, () => {
  if (!eventCfg.value) return;
  if (timerIsPointInTime.value) {
    syncPointsFromValue();
  } else if (timerIsDuration.value) {
    syncDurationFromValue();
  }
}, { immediate: true });

/** 定时事件必须有 node_config.event 段，缺失时补齐，供定时字段双向绑定 */
function ensureEventConfig() {
  if (!form.value.nodeConfig || typeof form.value.nodeConfig !== 'object') {
    form.value.nodeConfig = {};
  }
  if (!form.value.nodeConfig.event || typeof form.value.nodeConfig.event !== 'object') {
    form.value.nodeConfig.event = {};
  }
}

/** 事件类型切换：同步清理不再适用的事件字段，避免草稿残留脏数据 */
function handleEventTypeChange(type) {
  if (!type) {
    if (form.value.nodeConfig && typeof form.value.nodeConfig === 'object') {
      delete form.value.nodeConfig.event;
    }
    form.value.eventName = '';
    form.value.attachedToNodeCode = null;
    form.value.interrupting = null;
    return;
  }
  if (!isMessageEvent.value && !isErrorEvent.value) {
    form.value.eventName = '';
  }
  if (isBoundaryEvent.value) {
    // 第一阶段边界事件固定为中断型
    form.value.interrupting = true;
  } else {
    form.value.interrupting = null;
    form.value.attachedToNodeCode = null;
  }
  if (isTimerEvent.value) {
    ensureEventConfig();
  } else if (form.value.nodeConfig && typeof form.value.nodeConfig === 'object') {
    delete form.value.nodeConfig.event;
  }
}

// 事件节点：定时事件缺 event 段时补齐（含从后端回显的草稿）
watch(() => form.value.eventType, () => {
  if (isTimerEvent.value) {
    ensureEventConfig();
  }
}, { immediate: true });

function validateEventName(rule, value, callback) {
  if (!isMessageEvent.value) return callback();
  if (!value || !String(value).trim()) return callback(new Error('请输入消息名称'));
  callback();
}

function validateEventTimerValue(rule, value, callback) {
  if (!isTimerEvent.value) return callback();
  if (!value || !String(value).trim()) return callback(new Error('请输入时间值'));
  callback();
}

function validateEventAttachedNode(rule, value, callback) {
  if (!isBoundaryEvent.value) return callback();
  if (!value) return callback(new Error('请选择附着的任务节点'));
  callback();
}

function validateEventInterrupting(rule, value, callback) {
  if (!isBoundaryEvent.value) return callback();
  if (value !== true) return callback(new Error('第一阶段边界事件必须为中断型'));
  callback();
}


function getCurrentTabLabel() {
  const currentTab = tabsList.value.find(t => t.name === tabsValue.value);
  return currentTab ? currentTab.label : '节点扩展属性';
}

const rules = reactive({
    nodeRatio: computed(() => {
        const type = form.value.nodeRatioType;
        if (!type) return [];
        if (type === 'passRatio') {
            return [
                {required: true, message: "请输入", trigger: "change"},
                {validator: validatePassRatio, trigger: ["change", "blur"]}
            ];
        } else if (['passCount', 'rejectCount'].includes(type)) {
            return [
                {required: true, message: "请输入", trigger: "change"},
                {validator: validatePassCount, trigger: ["change", "blur"]}
            ];
        } else if (type === 'default') {
            return [
                {required: true, message: "请输入", trigger: "change"},
                {validator: validateDefault, trigger: ["change", "blur"]}
            ];
        }  else if (type === 'spel') {
            return [
                {required: true, message: "请输入", trigger: "change"},
                {validator: validateSpel, trigger: ["change", "blur"]}
            ];
        }
        // 其他类型不作限制
        return [];
    }),
    // 事件节点字段校验：与后端 FlowConfigUtil 的事件结构校验保持一致，尽量在保存前拦截
    eventName: [{validator: validateEventName, trigger: ['blur', 'change']}],
    eventTimerType: [{required: true, message: '请选择定时类型', trigger: 'change'}],
    eventTimerValue: [{validator: validateEventTimerValue, trigger: ['blur', 'change']}],
    attachedToNodeCode: [{validator: validateEventAttachedNode, trigger: 'change'}],
    interrupting: [{validator: validateEventInterrupting, trigger: 'change'}]
});

watch(() => form.value, n => {
  if (n) {
    emit('update:modelValue', n)
  }
  if (n.nodeRatioType) {
      let nodeRatio = '';
      if (/^passCount|rejectCount/.test(n.nodeRatioType)) {
          nodeRatio = n.nodeRatioType + "=";
      } else if (/^spel|default/.test(n.nodeRatioType)) {
          nodeRatio = n.nodeRatioType + "@@";
      }
      n.nodeRatio = nodeRatio + (n.nodeRatioValue ? n.nodeRatioValue : '')
  }
},{ deep: true });

function validatePassRatio(rule, value, callback) {
    if (value === '' || value === undefined || value === null) {
        callback(new Error('请输入通过率'));
    } else {
        const numValue = Number(value);
        if (isNaN(numValue)) {
            callback(new Error('请输入有效数字'));
        } else if (numValue < 0.001 || numValue > 100) {
            callback(new Error('通过率必须在0.001-100之间'));
        } else if (!/^\d+(\.\d{1,3})?$/.test(value)) {
            callback(new Error('通过率最多保留三位小数'));
        }  else {
            callback();
        }
    }
}

function validatePassCount(rule, value, callback) {
    if (value === '' || value === undefined || value === null) {
        callback(new Error('请输入固定人数'));
    } else {
        value = value.replace('passCount', '').replace('rejectCount', '').replace('=', '').trim();
        const reg = /^[1-9]\d*$/;
        if (!reg.test(value)) {
            callback(new Error('请输入正整数'));
        } else {
            callback();
        }
    }
}

function validateDefault(rule, value, callback) {
    value = value.replace('default@@', '').replace('=', '').trim();
    if (value === '' || value === undefined || value === null) {
        callback(new Error('请输入默认表达式'));
    } else if (!/^\$\{.*\}$/.test(value)) {
        callback(new Error('默认表达式必须以${开头，以}结尾'));
    } else {
        callback();
    }
}

function validateSpel(rule, value, callback) {
    value = value.replace('spel@@', '').replace('=', '').trim();
    if (value === '' || value === undefined || value === null) {
        callback(new Error('请输入spel表达式'));
    } else if (!/^\#\{.*\}$/.test(value)) {
        callback(new Error('spel表达式必须以#{开头，以}结尾'));
    } else {
        callback();
    }
}

function getNodeRatioDescription() {
    const type = form.value.nodeRatioType;
    switch (type) {
        case 'passRatio':
            return '请输入通过率(0.001-100)';
        case 'passCount':
            return '请输入固定通过人数，类型为正整数';
        case 'rejectCount':
            return '请输入固定驳回人数，类型为正整数';
        case 'default':
            return '请输入默认表达式,格式如: ${flag > 4}';
        case 'spel':
            return '请输入spel表达式，格式如: #{@user.eval(#flag)}';
        default:
            return '请输入';
    }
}

function handleClear() {
    // 处理清空操作的逻辑
    form.value.nodeRatioType = '';
    form.value.nodeRatioValue = '';
    form.value.nodeRatio = '';
}

// 删除办理人
function delPermission(index) {
  form.value.permissionFlag.splice(index, 1);
  permissionRows.value.splice(index, 1);
}
// 添加办理人
function addPermission() {
  form.value.permissionFlag.push("");
  permissionRows.value.push({ storageId: "", handlerName: "" });
}

// 发起人办理人标识：引擎创建任务时会替换为流程实例发起人（FlowCons.WARMFLOWINITIATOR）
const INITIATOR_FLAG = "warmFlowInitiator";
const INITIATOR_NAME = "发起人（流程发起人）";

// 快捷添加发起人：办理人配置为发起人，实现"谁发起、谁提交"
function addInitiator() {
  if (!Array.isArray(form.value.permissionFlag)) {
    form.value.permissionFlag = [];
  }
  if (form.value.permissionFlag.some(flag => flag === INITIATOR_FLAG)) return;
  form.value.permissionFlag.push(INITIATOR_FLAG);
  permissionRows.value.push({ storageId: INITIATOR_FLAG, handlerName: INITIATOR_NAME });
}
// 办理人手动输入，失焦获取权限名称
function inputBlur(event, index) {
  form.value.permissionFlag[index] = event.target.value;
}

// 添加在其他函数后面
function handleTabChange(activeTabName) {
    // 可以根据不同的 tab 做相应处理
    switch(activeTabName) {
        case '1':
            // 基础设置 tab
            break;
        case '2':
            // 办理人设置 tab
            getHandlerFeedback()
            break;
        default:
            // 自定义 tab
            break;
    }
}

/** 选择角色权限范围触发 */
function getPermissionFlag() {
  const pf = form.value.permissionFlag;
  form.value.permissionFlag = (typeof pf === 'string' && pf) ? pf.split("@@") : [""];
  if (form.value.listenerType && typeof form.value.listenerType === 'string') {
    const listenerTypes = form.value.listenerType.split(",");
    const lp = form.value.listenerPath;
    const listenerPaths = (typeof lp === 'string' && lp) ? lp.split("@@") : [];
    form.value.listenerRows = listenerTypes.map((type, index) => ({
      listenerType: type,
      listenerPath: listenerPaths[index]
    }));
  } else if (!Array.isArray(form.value.listenerRows)) {
    // 无监听器时也要保证行数组存在，共享组件 listenerConfig 依赖它增删行
    form.value.listenerRows = [];
  }
}

/** 查询表单定义列表 */
function getDefinition() {
  publishedList().then(response => {
    definitionList.value = response.data;
  });
}

/** 查询表单定义列表 */
function getHandlerDict() {
  handlerDict().then(response => {
    if (response.code === 200 && response.data) {
      dictList.value = response.data;
    }
  });
}


/** 办理人权限名称回显 */
async function getHandlerFeedback() {
  if (form.value.permissionFlag) {
      let permissionFlag = form.value.permissionFlag;
      handlerFeedback({storageIds: permissionFlag}).then(response => {
          if (response.code === 200 && response.data) {
            const resolved = response.data || [];
            // 后端解析不到的标识（如发起人 warmFlowInitiator）补充占位行，避免表格丢行
            const missing = (Array.isArray(permissionFlag) ? permissionFlag : [])
              .filter(id => id && !resolved.some(row => row.storageId === id))
              .map(id => ({ storageId: id, handlerName: id === INITIATOR_FLAG ? INITIATOR_NAME : id }));
            permissionRows.value = [...resolved, ...missing];
          }
    });
  }
}

/** 查询节点扩展属性 */
function getNodeExt() {
  nodeExt().then(response => {
    if (response.code === 200 && response.data) {
      response.data.forEach(e => {
        // 设置默认值
        e.childs.forEach(cItem => {
          if ([3, 4].includes(cItem.type)) {
            if (cItem?.dict) {
              if (form.value.ext?.[cItem.code] === undefined) {
                if (cItem.multiple) {
                  form.value.ext = Object.assign({}, form.value.ext, { [cItem.code]: [] });
                  cItem.dict.forEach(e => {
                    if (e.selected) form.value.ext[cItem.code].push(String(e.value));
                  });
                } else {
                  let value = cItem.dict.find(e => e.selected)?.value;
                  form.value.ext = Object.assign({}, form.value.ext, { [cItem.code]: value ? String(value) : null });
                }
              } else {
                if (cItem.multiple) {
                  // 处理为空、只选择一项的多选
                  let value = form.value.ext[cItem.code];
                  form.value.ext[cItem.code] = value
                    ? Array.isArray(value) ? value : value.split(",")
                    : [];
                }
              }
            }
          }
        });
        if (e.type === 1) {
          baseList.value.push(...(e?.childs || []));
          // 已经有值的默认展开，空的默认收起，避免打开抽屉先看到一长串空表单
          baseExtCollapsed.value = countFilled(baseList.value) === 0;
        } else if (e.type === 2) {
          tabsList.value.push({ label: e.name, name: e.code })
          buttonList.value[e.code] = e?.childs;
        }
      });
    }
  });
}

// 打开用户选择弹窗
function initUser() {
  userVisible.value = true;
}

// 获取选中用户数据
function handleUserSelect(checkedItemList) {
  form.value.permissionFlag = checkedItemList.map(e => {
    return e.storageId;
  }).filter(n => n);

  // 办理人表格展示
  permissionRows.value = checkedItemList;
}

// ========== 抄送（node_config.copy） ==========
// 抄送不产生待办、不改流程状态，只是到达节点时写一条留痕（flow_his_task.flow_status = copy
// + flow_user.type = 4）。存储结构见后端 CopyConfig：{ "copy": { "handlers": ["..."] } }。
const copyUserVisible = ref(false);
// 抄送人展示行：storageId 是写入 nodeConfig 的值，handlerName 只用于界面展示
const copyHandlerRows = ref([]);

/** 当前抄送人标识（node_config.copy.handlers），面板内的唯一读取入口 */
const copyHandlerIds = computed(() => {
  const config = form.value.nodeConfig;
  const handlers = config && typeof config === 'object' && config.copy && typeof config.copy === 'object'
    ? config.copy.handlers : null;
  return Array.isArray(handlers) ? handlers.filter(handler => handler) : [];
});

const copySummary = computed(() => {
  const rows = copyHandlerRows.value;
  if (rows.length === 0) return '未配置';
  const names = rows.map(row => row.handlerName || row.storageId);
  return names.length > 3 ? `${names.slice(0, 3).join('、')} 等 ${names.length} 人` : names.join('、');
});

/** 写入抄送人：没有抄送人时整段删掉，避免留下空的 copy 段 */
function syncCopyHandlers(ids) {
  const config = form.value.nodeConfig && typeof form.value.nodeConfig === 'object'
    ? { ...form.value.nodeConfig } : {};
  const handlers = (ids || []).filter(id => id);
  if (handlers.length > 0) {
    config.copy = { ...(config.copy && typeof config.copy === 'object' ? config.copy : {}), handlers };
  } else {
    delete config.copy;
  }
  form.value.nodeConfig = config;
}

/** 抄送人名称回显：先按已知行补齐，缺失的再问一次宿主的 handlerFeedback */
async function loadCopyHandlerNames() {
  const ids = copyHandlerIds.value;
  if (ids.length === 0) {
    copyHandlerRows.value = [];
    return;
  }
  const known = new Map(copyHandlerRows.value.map(row => [row.storageId, row.handlerName]));
  const pending = ids.filter(id => !known.get(id));
  let resolved = [];
  if (pending.length > 0) {
    try {
      const response = await handlerFeedback({ storageIds: pending });
      resolved = response && response.code === 200 ? (response.data || []) : [];
    } catch (e) {
      resolved = [];
    }
  }
  // 请求期间可能已经切到别的节点：结果回来时标识对不上就丢弃，避免把上一个节点的名称写到当前节点
  if (JSON.stringify(copyHandlerIds.value) !== JSON.stringify(ids)) {
    return;
  }
  copyHandlerRows.value = ids.map(id => {
    if (id === INITIATOR_FLAG) return { storageId: id, handlerName: INITIATOR_NAME };
    const hit = resolved.find(row => row.storageId === id);
    // 宿主解析不到时退回标识本身，至少不丢行
    return { storageId: id, handlerName: (hit && hit.handlerName) || known.get(id) || id };
  });
}

function openCopyUser() {
  copyUserVisible.value = true;
  loadCopyHandlerNames();
}

/** 选择器确认：整体覆盖抄送人，未选则清空 */
function handleCopyUserSelect(checkedItemList) {
  const rows = (checkedItemList || []).filter(row => row && row.storageId);
  copyHandlerRows.value = rows;
  syncCopyHandlers(rows.map(row => row.storageId));
}

function removeCopyHandler(index) {
  const rows = [...copyHandlerRows.value];
  rows.splice(index, 1);
  copyHandlerRows.value = rows;
  syncCopyHandlers(rows.map(row => row.storageId));
}

/** 快捷添加发起人：引擎在抄送时把它替换成流程实例的发起人 */
function addCopyInitiator() {
  if (copyHandlerIds.value.includes(INITIATOR_FLAG)) return;
  const rows = [...copyHandlerRows.value, { storageId: INITIATOR_FLAG, handlerName: INITIATOR_NAME }];
  copyHandlerRows.value = rows;
  syncCopyHandlers(rows.map(row => row.storageId));
}

// 增加行 / 删除行：由共享组件 listenerConfig 处理

const filteredNodes = computed(() => {
  let previousNodes = getPreviousNodes(props.nodes, props.skips, form.value.nodeCode)
  return previousNodes.filter(node => !["start", "serial", "parallel", 'inclusive'].includes(node.type));
});

// 移动端/平板检测
const isMobile = computed(() => {
  if (typeof window === 'undefined') return false;
  return window.innerWidth <= 768;
});

getPermissionFlag();
// TODO form 开发中
// getDefinition();

// getHandlerDict();

// 监听器已收进基础设置分组，没有页签切换可挂钩，改为初始化时取一次候选
getNodeExt();
// 扩展属性（KV）行初始化
initKvRows();
// 外部 nodeConfig 变化（切换节点 / 默认变量写回）时同步 KV 行；自身写回通过快照跳过
watch(() => form.value.nodeConfig, () => {
  if (JSON.stringify(form.value.nodeConfig || {}) !== lastNodeConfigSnapshot) {
    initKvRows();
  }
}, { deep: true });
// 抄送人名称回显：没有配置抄送人时不会发请求。
// 必须监听节点切换：属性面板复用同一个 form 对象（propertySetting 原地改写），
// 不监听的话切到另一个节点时还显示上一个节点的抄送人，
// 再点「选择」确认就会把上一个节点的抄送人写进当前节点。
watch(() => JSON.stringify(copyHandlerIds.value), () => {
  loadCopyHandlerNames();
}, { immediate: true });

// 表单必填校验
// 注意：必须 await 表单校验结果。ElForm 的 validate(callback) 回调是异步的，
// 旧写法在回调 reject 之前就被 tabsValidate 的 resolve(true) 抢先 resolve，导致校验永远通过。
async function validate() {
  tabsValue.value = "1";
  await proxy.$nextTick();
  try {
    await proxy.$refs.formRef.validate();
  } catch (fields) {
    // 分组收起时校验红字是不可见的，命中监听器字段的错误就先把分组展开
    if (fields && Object.keys(fields).some((key) => String(key).startsWith('listenerRows.'))) {
      proxy.$refs.listenerSection?.open();
    }
    // 同理：事件分组收起时命中事件字段/定时配置的错误也要先展开
    if (fields && Object.keys(fields).some((key) => String(key) === 'eventType' || String(key) === 'eventName'
        || String(key).startsWith('nodeConfig.event') || String(key) === 'attachedToNodeCode'
        || String(key) === 'interrupting')) {
      proxy.$refs.eventSection?.open();
    }
    throw false;
  }
  if (proxy.$refs.nodeBase && proxy.$refs.nodeBase.length > 0) {
    if (!await proxy.$refs.nodeBase[0].validate()) {
      // 同理：节点扩展属性收起时也要展开，否则保存"没反应"
      baseExtCollapsed.value = false;
      throw false;
    }
  }
  return tabsValidate();
}

async function tabsValidate() {
  let addTabsList = tabsList.value.slice(tabsList.value.length);
  for (const e of addTabsList) {
    tabsValue.value = e.name;
    await proxy.$nextTick();
    if (!await proxy.$refs[`nodeExtList_${e.name}`][0].validate()) return false;
  }
  return true;
}

defineExpose({
  validate
})
</script>

<style scoped lang="scss">
@import '@/assets/styles/_common.scss';

/* ========== 容器布局 ========== */
.between {
  width: 100%;
  html.dark & {
    background: var(--wf-bg-color, #141414);
    border-radius: var(--wf-radius-lg, 12px);
  }
}
.betweenForm {
  border-top: 0;
  width: 100%;

  :deep(.el-form-item) {
    margin-bottom: 22px;
  }

  :deep(.el-form-item__label) {
    width: 110px !important;
    min-width: 110px !important;
    max-width: 110px !important;
    padding-right: 12px;
    font-size: 13px;
    line-height: 24px;
    white-space: nowrap;
  }

  .base-settings-content :deep(.el-form-item__error) {
    position: static;
    padding-top: 4px;
    line-height: 1.3;
  }
}

.ratio-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.ratio-type {
  flex: 0 1 38%;
  width: 38%;
  min-width: 96px;
  max-width: 140px;
}

.ratio-value {
  flex: 1 1 0;
  min-width: 0;
}

/* ========== 抄送人：标签列表 + 选择入口 ========== */
.copy-picker {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.copy-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  width: 100%;
}

.copy-tag {
  border-radius: 12px;
  font-size: 12px;
  background: var(--wf-primary-lighter, #f0f7ff);
  border-color: rgba(64, 158, 255, 0.25);
  color: var(--wf-primary, #409eff);

  html.dark & {
    background: rgba(64, 158, 255, 0.12);
    border-color: rgba(64, 158, 255, 0.3);
  }
}

.copy-empty {
  color: var(--wf-text-secondary, #909399);
  font-size: 12px;
  line-height: 22px;
}

.copy-actions {
  display: flex;
  align-items: center;
  gap: 8px;

  .add-row-btn {
    width: auto;
    flex: 0 0 auto;
    padding: 0 14px;
  }
}

.field-hint {
  margin-top: 6px;
  color: var(--wf-text-secondary, #909399);
  font-size: 12px;
  line-height: 1.4;
  width: 100%;
}

/* 扩展属性：三列输入在窄侧栏内保持可用，操作列不参与挤压 */
.kv-table {
  :deep(.el-table__cell) {
    padding: 6px 0;
  }
  :deep(.cell) {
    padding: 0 6px;
  }
}

/* 定时时长选择器：数值 + 单位 + 高级切换 */
.duration-picker {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.duration-unit {
  width: 96px;
  flex-shrink: 0;
}

.advanced-link {
  flex-shrink: 0;
  margin-left: auto;
  font-size: 12px;
}

.advanced-input {
  flex: 1;
}

/* 每日时间点列表：时刻选择 + 删除按钮 */
.time-point-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  margin-bottom: 8px;
}

.time-point-row .el-date-editor {
  flex: 1;
  max-width: 180px;
}

.time-point-row .el-button {
  flex-shrink: 0;
  padding: 4px;
}

/* 时区下拉占满整行，与其他表单项对齐 */
.timezone-select {
  width: 100%;
}

.reject-item :deep(.el-form-item__content) {
  display: flex;
  flex-direction: column;
  align-items: stretch;
}

.reject-item :deep(.el-select) {
  width: 100%;
}

/* 引入公共样式：现代化页签 + 基础配置卡片 + 监听器/办理人卡片 + 表格表单左对齐 */
@include modern-tabs;
@include base-settings-card;
@include section-card;
@include table-form-align;
@include responsive-adaption;

/* ========== 手机端特有适配（between 独有） ========== */
@media (max-width: 768px) {
  /* 协作方式/自定义表单 radio-card：手机端保持一行横向排列，缩小间距 */
  .radio-card-group {
    gap: 6px;
    flex-wrap: nowrap;
    overflow-x: auto;
    &::-webkit-scrollbar { display: none; }
  }
  .radio-card-item {
    padding: 6px 12px;
    font-size: 12px;
    white-space: nowrap;
    flex-shrink: 0;
  }

  /* 基础设置表单 label：手机端仍单行，略缩字号 */
  .betweenForm::v-deep(.el-form-item__label) {
    min-width: 108px !important;
    max-width: 108px !important;
    width: 108px !important;
    font-size: 12px !important;
    white-space: nowrap !important;
    line-height: 24px;
  }

  .ratio-row {
    flex-wrap: wrap;
  }
  .ratio-type,
  .ratio-value {
    flex: 1 1 100%;
    width: 100%;
  }

  /* 票签策略：select + input 堆叠显示 */
  .betweenForm::v-deep(.el-form-item) {
    .el-form-item__content {
      display: flex !important;
      flex-wrap: wrap !important;
      gap: 6px;
    }
    .el-select,
    .el-input {
      width: 100% !important;
      margin-left: 0 !important;
      min-width: 100%;
    }
  }

  /* 驳回节点 select 缩小 */
  .ext-attributes-section { margin-top: 8px; }

  /* 办理人表格：隐藏滚动条，操作列固定宽度 */
  .handler-table-mobile {
    :deep(.el-table) {
      table-layout: fixed !important;
      width: 100% !important;
      overflow: hidden;
    }
    :deep(.el-table__header-wrapper),
    :deep(.el-table__body-wrapper) {
      overflow: hidden !important;
    }

    /* ====== 手机端：三列百分比布局，操作列固定52px ====== */
    :deep(.el-table__header-wrapper) {
      th:nth-child(1) { width: calc(50% - 26px) !important; min-width: 0 !important; & .cell { width: 100%; } }
      th:nth-child(2) { width: calc(50% - 26px) !important; min-width: 0 !important; & .cell { width: 100%; } }
      th:nth-child(3) { width: 52px !important; min-width: 0 !important; max-width: none !important; & .cell { width: 100%; text-align: center; justify-content: center; } }
    }
    :deep(.el-table__body-wrapper) {
      td:nth-child(1) { width: calc(50% - 26px) !important; min-width: 0 !important; & .cell { width: 100%; } }
      td:nth-child(2) { width: calc(50% - 26px) !important; min-width: 0 !important; & .cell { width: 100%; } }
      td:nth-child(3) { width: 52px !important; min-width: 0 !important; max-width: none !important; & .cell { width: 100%; display: flex; justify-content: center; align-items: center; padding-left: 4px; padding-right: 4px; box-sizing: border-box; } }
    }

    /* ====== PC端：前两列自适应，操作列锁定42px ====== */
    &:not(.is-mobile) {
      :deep(.el-table) {
        table-layout: auto !important;
      }
      :deep(.el-table__header-wrapper),
      :deep(.el-table__body-wrapper) {
        overflow-x: hidden !important;

        /* PC端操作列强制42px */
        th:nth-child(3), td:nth-child(3) {
          width: 42px !important;
          min-width: 42px !important;
          max-width: 42px !important;
        }
        th:nth-child(3) .cell {
          text-align: center;
          justify-content: center;
        }
        td:nth-child(3) .cell {
          display: flex;
          justify-content: center;
          align-items: center;
          padding-left: 4px;
          padding-right: 4px;
          box-sizing: border-box;
        }
      }
    }
  }

  /* 添加行 + 选择 */
  .action-buttons {
    margin-top: 10px;
  }

  /* 手机端：人员选择弹框占满行宽（用非 scoped 覆盖 append-to-body 弹框） */
  :global(.mobile-user-dialog) {
    width: 96% !important;
    max-width: 100vw !important;
    margin: 0 auto !important;

    .el-dialog__header {
      padding: 14px 16px;
      .el-dialog__title { font-size: 16px; }
      .el-dialog__headerbtn { top: 12px; right: 10px; }
    }

    .el-dialog__body {
      padding: 12px;
      max-height: calc(100vh - 56px);
      overflow-y: auto;
    }
  }
}

@media (max-width: 480px) {
  .betweenForm::v-deep(.el-form-item__label) {
    min-width: 96px !important;
    max-width: 96px !important;
    width: 96px !important;
    font-size: 12px !important;
    white-space: nowrap !important;
  }
  .radio-card-text {
    font-size: 12px;
  }
  .radio-card-tip {
    display: none;
  }
}

.placeholder { color: #828f9e; font-size: 12px; }
.mt5 { margin-top: 5px; }
.mr5 { margin-right: 5px; }
.ml5 { margin-left: 5px; }
.flex-hc { display: flex; align-items: center; }

/* ========== 虚线增加行按钮（与 baseInfo 风格一致） ========== */
.action-buttons {
  margin-top: 12px;
}

.add-row-btn {
  width: 100%;
  border: 1.5px dashed var(--wf-primary, #409eff) !important;
  color: var(--wf-primary, #409eff) !important;
  background: transparent !important;
  border-radius: 8px;
  transition: all 0.3s ease;
  height: 24px;
  font-size: 12px;
  letter-spacing: 1px;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;

  &:hover {
    background: var(--wf-primary-light, #ecf5ff) !important;
    border-style: solid !important;
    border-color: var(--wf-primary, #409eff) !important;
    box-shadow: var(--wf-shadow-primary, 0 2px 8px rgba(64, 158, 255, 0.3));
  }

  &.add-row-btn-secondary {
    border-color: var(--wf-text-secondary, #909399) !important;
    color: var(--wf-text-secondary, #909399) !important;

    &:hover {
      border-color: var(--wf-primary, #409eff) !important;
      color: var(--wf-primary, #409eff) !important;
      background: var(--wf-primary-light, #ecf5ff) !important;
      border-style: solid !important;
      box-shadow: var(--wf-shadow-primary, 0 2px 8px rgba(64, 158, 255, 0.3));
    }
  }
}

/* ========== 人员选择弹窗：iOS 风格外壳 ==========
   弹窗为 append-to-body，不在组件作用域内，必须用扁平的 :global(完整选择器)。
   不能写成 :global(.person-select-dialog) { .el-dialog__header {} } 这种「:global 内再嵌套子选择器」，
   否则 Vue 会给内层注入 scoped 属性而失效，且头部声明会漏到弹窗根节点、把 .el-dialog 默认白底覆盖成透明渐变。 */
:global(.person-select-dialog) {
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 16px 56px rgba(0, 0, 0, 0.18);
  background-color: var(--el-dialog-bg-color, #fff);
}
:global(.person-select-dialog .el-dialog__header) {
  position: relative;
  margin: 0;
  padding: 18px 24px;
  background-color: var(--el-dialog-bg-color, #fff);
  border-bottom: 1px solid var(--wf-border-lighter, #ebeef5);
}
:global(.person-select-dialog .el-dialog__title) {
  font-weight: 600;
  font-size: 16px;
  color: var(--wf-text-primary, #303133);
}
/* 关闭按钮：相对头部垂直居中，避免偏下错位 */
:global(.person-select-dialog .el-dialog__headerbtn) {
  top: 50%;
  right: 16px;
  width: 32px;
  height: 32px;
  margin: 0;
  transform: translateY(-50%);
}
:global(.person-select-dialog .el-dialog__body) {
  padding: 20px 24px;
  background-color: var(--el-dialog-bg-color, #fff);
}
/* 表头：覆盖全局 ruoyi 的灰底(#f8f8f9 !important)，统一白底；弹窗 append-to-body 需 :global */
:global(.person-select-dialog .el-table th.el-table__cell),
:global(.person-select-dialog .el-table__header-wrapper th) {
  background-color: var(--el-dialog-bg-color, #fff) !important;
}
:global(html.dark .person-select-dialog),
:global(html.dark .person-select-dialog .el-dialog__header),
:global(html.dark .person-select-dialog .el-dialog__body) {
  background: var(--wf-bg-color, #1f1f1f);
}
:global(html.dark .person-select-dialog .el-dialog__header) {
  border-bottom-color: var(--wf-border-color, #333333);
}
:global(html.dark .person-select-dialog .el-dialog__title) {
  color: var(--wf-text-primary, #e0e0e0);
}
:global(html.dark .person-select-dialog .el-table th.el-table__cell),
:global(html.dark .person-select-dialog .el-table__header-wrapper th) {
  background-color: var(--wf-bg-color, #1f1f1f) !important;
  color: #c0c4cc !important;
}

/* 蓝色主题 - 办理人（扁平：保留蓝色标题/图标做语义，去卡片底/边框/渐变头） */
.section-blue {
  .section-card-header {
    background: transparent;
    border-bottom-color: var(--wf-border-lighter, #ebeef5);
    html.dark & { background: transparent; border-bottom-color: var(--wf-border-color, #333333); }
  }
  .section-card-icon { color: var(--wf-primary, #409eff); }
  .section-card-title { color: var(--wf-primary, #409eff); }
}

/* 紫色主题 - 监听器（扁平：保留紫色标题/图标做语义，去卡片底/边框/渐变头） */
.section-purple {
  .section-purple-header {
    background: transparent;
    border-bottom-color: var(--wf-border-lighter, #ebeef5);
    html.dark & { background: transparent; border-bottom-color: var(--wf-border-color, #333333); }
  }
  .section-purple-icon { color: #8960dc; }
  .section-purple-title { color: #8960dc; }
}

/* ========== 卡片式单选框 ========== */
.radio-card-group {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  &.radio-card-sm { gap: 8px; }
}
.radio-card-item {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border-radius: var(--wf-radius, 8px);
  border: 1.5px solid var(--wf-border-color, #dcdfe6);
  background: var(--wf-bg-white, #fff);
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
  font-size: 12px;
  color: var(--wf-text-regular, #606266);
  html.dark & {
    background: var(--wf-bg-color, #1a1a1a);
    border-color: var(--wf-border-color, #333333);
    color: var(--wf-text-regular, #b0b0b0);
  }
  &:hover {
    border-color: var(--wf-primary, #409eff);
    background: var(--wf-primary-lighter, #f0f7ff);
    color: var(--wf-primary, #409eff);
    html.dark & { background: rgba(64, 158, 255, 0.08); }
    .radio-card-dot { border-color: var(--wf-primary, #409eff); background: transparent; }
  }
  &.is-checked {
    border-color: var(--wf-primary, #409eff);
    background: var(--wf-primary-light, #ecf5ff);
    color: var(--wf-primary, #409eff);
    font-weight: 500;
    html.dark & { background: rgba(64, 158, 255, 0.12); }
    .radio-card-dot {
      border-color: var(--wf-primary, #409eff);
      background: var(--wf-primary, #409eff);
      box-shadow: inset 0 0 0 3px var(--wf-bg-white, #fff);
      html.dark & { box-shadow: inset 0 0 0 3px var(--wf-bg-color, #141414); }
    }
  }
  .radio-card-dot {
    width: 14px; height: 14px; min-width: 14px;
    border-radius: 50%; border: 2px solid var(--wf-border-color, #dcdfe6);
    transition: all 0.25s ease; background: var(--wf-bg-white, #fff);
    html.dark & { border-color: var(--wf-border-color, #444444); background: var(--wf-bg-color, #1a1a1a); }
  }
  .radio-card-text { line-height: 1; }
  .radio-card-tip {
    color: var(--wf-text-placeholder, #c0c4cc); transition: color 0.25s ease;
    .is-checked & { color: var(--wf-primary, #409eff); opacity: 0.7; }
  }
}

/* 属性面板中的紧凑选择项 */
.betweenForm .radio-card-group {
  gap: 6px;
  align-items: center;
}

.betweenForm .radio-card-item {
  min-height: 28px;
  padding: 4px 9px;
  gap: 5px;
  font-size: 12px;
  border-width: 1px;
}

.betweenForm .radio-card-dot {
  width: 13px;
  height: 13px;
  min-width: 13px;
  border-width: 1.5px;
}

.betweenForm .radio-card-tip {
  font-size: 12px;
}

/* ========== 节点扩展属性区域（基础设置内 - 蓝色主题） ========== */
.ext-attributes-section {
  margin-top: 12px;
  border-radius: var(--wf-radius-lg, 12px);
  overflow: hidden;
  border: 1px solid var(--wf-border-light, #e4e7ed);
  background: var(--wf-bg-white, #fff);
  box-shadow: var(--wf-shadow-sm, 0 1px 4px rgba(0, 0, 0, 0.04));
  transition: all 0.3s ease;
  html.dark & { border-color: var(--wf-border-color, #333333); background: var(--wf-bg-color, #141414); }
  &:hover {
    box-shadow: var(--wf-shadow, 0 2px 12px rgba(0, 0, 0, 0.06));
    html.dark & { box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3); }
  }
}
.ext-attributes-header {
  display: flex; align-items: center; gap: 8px; padding: 12px 16px;
  background: linear-gradient(135deg, var(--wf-primary-light, #ecf5ff) 0%, var(--wf-primary-lighter, #f0f7ff) 100%);
  border-bottom: 1px solid rgba(64, 158, 255, 0.12);
  html.dark & {
    background: linear-gradient(135deg, rgba(64, 158, 255, 0.08) 0%, rgba(43, 125, 233, 0.05) 100%);
    border-bottom-color: rgba(64, 158, 255, 0.12);
  }
}
.ext-attributes-icon {
  width: 20px; height: 20px;
  display: flex; align-items: center; justify-content: center;
  color: var(--wf-primary, #409eff);
  svg { width: 16px; height: 16px; }
}
.ext-attributes-title {
  font-size: 13px; font-weight: 600; color: var(--wf-primary, #409eff); letter-spacing: 0.3px;
}
.ext-attributes-badge {
  display: inline-flex; align-items: center; justify-content: center;
  padding: 1px 7px; font-size: 10px; font-weight: 600;
  color: var(--wf-primary, #409eff); background: rgba(64, 158, 255, 0.12); border-radius: 8px;
  flex-shrink: 0;
  html.dark & { background: rgba(64, 158, 255, 0.2); color: var(--wf-primary, #409eff); }

  /* 一项都没填时压成灰色，避免空分组也顶着主色抢注意力 */
  &.is-empty {
    color: var(--wf-text-secondary, #909399);
    background: rgba(144, 147, 153, 0.14);
    html.dark & { background: rgba(144, 147, 153, 0.24); color: #a8abb2; }
  }
}

/* 折叠状态下的一行摘要 */
.ext-collapse-summary {
  flex: 1; min-width: 0;
  font-size: 11.5px; color: var(--wf-text-regular, #606266);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  html.dark & { color: #a8abb2; }
}
.ext-attributes-content {
  padding: 14px 16px; background: var(--wf-bg-color, #fafbfc);
  html.dark & { background: var(--wf-bg-color, #141414); }
}

/* ========== 折叠功能样式 ========== */
.ext-header-clickable {
  cursor: pointer;
  user-select: none;
  transition: background 0.25s ease;

  &:hover {
    background: linear-gradient(135deg, rgba(64, 158, 255, 0.1) 0%, rgba(43, 125, 233, 0.06) 100%);
    html.dark & {
      background: linear-gradient(135deg, rgba(64, 158, 255, 0.12) 0%, rgba(43, 125, 233, 0.08) 100%);
    }
  }

  &:active {
    opacity: 0.85;
  }
}

.ext-collapse-arrow {
  margin-left: auto;
  width: 20px; height: 20px;
  display: flex; align-items: center; justify-content: center;
  color: var(--wf-primary, #409eff);
  opacity: 0.6;
  transition: opacity 0.25s ease;

  svg { width: 18px; height: 18px; }

  .ext-header-clickable:hover & {
    opacity: 1;
  }
}

.arrow-rotated {
  transform: rotate(180deg);
  transition: transform 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 折叠状态：去掉底部边框 */
.is-collapsed {
  border-bottom-width: 1px !important;

  .ext-attributes-header {
    border-bottom: none;
  }
}

/* 展开/折叠过渡动画 */
.ext-collapse-enter-active,
.ext-collapse-leave-active {
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}
.ext-collapse-enter-from,
.ext-collapse-leave-to {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
}
.ext-collapse-enter-to,
.ext-collapse-leave-from {
  opacity: 1;
  max-height: 2000px;
}

/* ========== 绿色主题（动态页签） ========== */
.ext-secondary {
  border-color: rgba(103, 194, 58, 0.25);
  html.dark & { border-color: rgba(103, 194, 58, 0.2); }
  .ext-secondary-header {
    background: linear-gradient(135deg, rgba(103, 194, 58, 0.08) 0%, rgba(82, 155, 46, 0.04) 100%);
    border-bottom-color: rgba(103, 194, 58, 0.12);
    html.dark & {
      background: linear-gradient(135deg, rgba(103, 194, 58, 0.1) 0%, rgba(82, 155, 46, 0.06) 100%);
      border-bottom-color: rgba(103, 194, 58, 0.15);
    }
  }
  .ext-secondary-title { color: #67c23a; }
  .ext-secondary-badge { color: #67c23a; background: rgba(103, 194, 58, 0.12); html.dark & { background: rgba(103, 194, 58, 0.2); } }
  .ext-icon-puzzle { color: #67c23a; }
  &:hover { box-shadow: 0 2px 12px rgba(103, 194, 58, 0.1); html.dark & { box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3); } }
}

/* ========== 表格内表单元素左对齐（关键修复） ========== */
.section-card-body {
  /* 穿透表格内的所有 el-form-item，去掉 label 占位 */
  ::v-deep(.el-table .el-form-item) {
    margin-bottom: 0;

    /* 彻底隐藏 label 区域 */
    .el-form-item__label {
      display: none !important;
      width: 0 !important;
      padding-right: 0 !important;
      min-height: 0 !important;
      height: 0 !important;
      overflow: hidden !important;
    }

    /* 内容区域紧贴左侧 */
    .el-form-item__content {
      display: block !important;
      margin-left: 0 !important;
    }
  }
}

::v-deep(.permissionItem) {
  display: inline-block;
  width: 100%;
  .el-form-item__content { display: block; }
  .inputGroup {
    display: flex; align-items: center; margin-bottom: 10px;
    .Icon { width:14px;height:14px;margin-left:5px;cursor:pointer;color:#fff;background:#94979d;border-radius:50%;padding:2px;&:hover{background:#f0f2f5;color:#94979d;} }
    .btn { height:18px;margin-left:5px;color:#fff;background:#94979d;border-radius:5px;padding:6px;font-size:10px;border:0;&:hover{background:#f0f2f5;color:#94979d;} }
  }
}

::v-deep(.listenerItem) {
  display: inline-block;
  width: 100%;
  .el-form-item__label { display:none!important;width:0!important;min-height:0!important;height:0!important;overflow:hidden!important;padding-right:0!important; }
  .el-form-item__content { margin-left:0!important;display:block!important; }
  .el-table .el-form-item { margin-bottom:0; }
}
</style>
