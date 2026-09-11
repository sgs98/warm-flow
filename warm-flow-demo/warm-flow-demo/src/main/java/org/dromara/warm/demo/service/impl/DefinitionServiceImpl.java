package org.dromara.warm.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.warm.demo.common.BizException;
import org.dromara.warm.demo.mapper.DemoFlowDefinitionMapper;
import org.dromara.warm.demo.service.DefinitionService;
import org.dromara.warm.demo.vo.DefinitionSummaryVo;
import org.dromara.warm.demo.vo.PageVo;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.utils.ObjectUtil;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.dromara.warm.flow.orm.entity.FlowDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 流程定义业务实现，承接 Controller 与 Warm-Flow 引擎之间的调用。
 *
 * @author may
 * @since 2026/9/5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefinitionServiceImpl implements DefinitionService {

    private final DemoFlowDefinitionMapper flowDefinitionMapper;

    public PageVo<DefinitionSummaryVo> page(Integer pageNum, Integer pageSize, String keyword,
                                            String category, Integer isPublish) {
        int pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int ps = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        LambdaQueryWrapper<FlowDefinition> query = Wrappers.lambdaQuery();
        if (StringUtils.isNotEmpty(keyword)) {
            String like = keyword.trim();
            query.and(w -> w.like(FlowDefinition::getFlowCode, like)
                .or().like(FlowDefinition::getFlowName, like));
        }
        if (StringUtils.isNotEmpty(category)) {
            query.eq(FlowDefinition::getCategory, category);
        }
        if (ObjectUtil.isNotNull(isPublish)) {
            query.eq(FlowDefinition::getIsPublish, isPublish);
        }
        query.orderByDesc(FlowDefinition::getId);
        Page<FlowDefinition> result = flowDefinitionMapper.selectPage(new Page<>(pn, ps), query);

        List<DefinitionSummaryVo> rows = result.getRecords().stream()
            .map(this::toSummary)
            .collect(Collectors.toList());
        return new PageVo<>(result.getTotal(), pn, ps, rows);
    }

    public DefJson detail(Long id) {
        Definition definition = FlowEngine.defService().getById(id);
        if (ObjectUtil.isNull(definition)) {
            throw BizException.notFound("流程定义不存在: " + id);
        }
        return FlowEngine.defService().queryDesign(id);
    }

    public Long save(DefJson defJson) {
        if (ObjectUtil.isNull(defJson)) {
            throw BizException.badRequest("保存数据不能为空");
        }
        if (StringUtils.isEmpty(defJson.getFlowName()) || StringUtils.isEmpty(defJson.getFlowCode())) {
            throw BizException.badRequest("流程编码和流程名称不能为空");
        }
        try {
            FlowEngine.defService().saveDef(defJson, false);
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("保存流程定义失败", e);
            throw BizException.badRequest("保存流程定义失败: " + e.getMessage());
        }
        return resolveSavedId(defJson);
    }

    public void publish(Long id) {
        exists(id);
        try {
            FlowEngine.defService().publish(id);
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    public Long copy(Long id) {
        exists(id);
        try {
            FlowEngine.defService().copyDef(id);
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        }
        Definition origin = FlowEngine.defService().getById(id);
        List<Definition> copies = FlowEngine.defService().getByFlowCode(origin.getFlowCode());
        return copies.stream().mapToLong(Definition::getId).max().orElse(id);
    }

    public void remove(Long id) {
        exists(id);
        try {
            FlowEngine.defService().removeDef(new ArrayList<>(java.util.Collections.singletonList(id)));
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    private void exists(Long id) {
        if (ObjectUtil.isNull(id) || ObjectUtil.isNull(FlowEngine.defService().getById(id))) {
            throw BizException.notFound("流程定义不存在: " + id);
        }
    }

    private Long resolveSavedId(DefJson defJson) {
        if (ObjectUtil.isNotNull(defJson.getId())) {
            return defJson.getId();
        }
        // 新增保存后根据编码取最新 id（含复制时生成的版本）
        List<Definition> list = FlowEngine.defService().getByFlowCode(defJson.getFlowCode());
        java.util.Optional<Long> maxId = list.stream().map(Definition::getId).max(Long::compareTo);
        return maxId.orElse(null);
    }

    private DefinitionSummaryVo toSummary(FlowDefinition d) {
        DefinitionSummaryVo vo = new DefinitionSummaryVo();
        vo.setId(d.getId());
        vo.setFlowCode(d.getFlowCode());
        vo.setFlowName(d.getFlowName());
        vo.setModelValue(d.getModelValue());
        vo.setCategory(d.getCategory());
        vo.setVersion(d.getVersion());
        vo.setIsPublish(d.getIsPublish());
        vo.setActivityStatus(d.getActivityStatus());
        vo.setFormCustom(d.getFormCustom());
        vo.setFormPath(d.getFormPath());
        vo.setListenerType(d.getListenerType());
        vo.setListenerPath(d.getListenerPath());
        vo.setExt(d.getExt());
        vo.setCreateTime(d.getCreateTime());
        vo.setUpdateTime(d.getUpdateTime());
        return vo;
    }
}
