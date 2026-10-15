package org.dromara.warm.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.warm.demo.common.BizException;
import org.dromara.warm.demo.dto.PageQuery;
import org.dromara.warm.demo.mapper.DemoFlowDefinitionMapper;
import org.dromara.warm.demo.service.DefinitionService;
import org.dromara.warm.demo.vo.DefinitionExportVo;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * 流程定义配置（def_config）中开启自动审批的键名，与引擎 AutoPassAdvancer 保持一致。
     */
    private static final String AUTO_PASS_KEY = "autoPass";

    private final DemoFlowDefinitionMapper flowDefinitionMapper;

    public PageVo<DefinitionSummaryVo> page(PageQuery pageQuery, String keyword,
                                            String category, Integer isPublish) {
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
        Page<FlowDefinition> result = flowDefinitionMapper.selectPage(pageQuery.build(), query);

        List<DefinitionSummaryVo> rows = result.getRecords().stream()
            .map(this::toSummary)
            .collect(Collectors.toList());
        return new PageVo<>(result.getTotal(),
            pageQuery.normalizedPageNum(), pageQuery.normalizedPageSize(), rows);
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
            return FlowEngine.defService().copyDefReturnId(id);
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        }
    }

    public void updateAutoPass(Long id, Boolean autoPass) {
        if (ObjectUtil.isNull(autoPass)) {
            throw BizException.badRequest("自动审批开关不能为空");
        }
        requireDefinition(id);
        Definition update = FlowEngine.newDef().setId(id);
        // def_config 显式声明 autoPass（true/false 都写），引擎只读定义配置，不回退读 ext
        Map<String, Object> defConfig = new HashMap<>(4);
        defConfig.put(AUTO_PASS_KEY, autoPass);
        update.setDefConfig(FlowEngine.jsonConvert.objToStr(defConfig));
        // 只带主键与 def_config 更新，避免整行回写覆盖并发修改；已发布版本同样可改，引擎办理时实时读取
        if (!FlowEngine.defService().updateById(update)) {
            throw BizException.badRequest("更新自动审批失败: " + id);
        }
    }

    public DefinitionExportVo export(Long id) {
        Definition definition = requireDefinition(id);
        DefinitionExportVo exportVo = new DefinitionExportVo();
        exportVo.setFileName(buildExportFileName(definition));
        exportVo.setContent(FlowEngine.defService().exportJson(id));
        return exportVo;
    }

    public Long importFile(MultipartFile file) {
        if (ObjectUtil.isNull(file) || file.isEmpty()) {
            throw BizException.badRequest("请选择要导入的流程定义 json 文件");
        }
        try {
            return FlowEngine.defService().importIs(file.getInputStream()).getId();
        } catch (FlowException e) {
            throw BizException.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("导入流程定义失败", e);
            throw BizException.badRequest("导入流程定义失败: " + e.getMessage());
        }
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
        requireDefinition(id);
    }

    private Definition requireDefinition(Long id) {
        Definition definition = ObjectUtil.isNull(id) ? null : FlowEngine.defService().getById(id);
        if (ObjectUtil.isNull(definition)) {
            throw BizException.notFound("流程定义不存在: " + id);
        }
        return definition;
    }

    /**
     * 用流程编码与版本号拼导出文件名，并替换掉文件名中的非法字符。
     */
    private String buildExportFileName(Definition definition) {
        String flowCode = StringUtils.isEmpty(definition.getFlowCode())
            ? "flow" : definition.getFlowCode().trim();
        String version = StringUtils.isEmpty(definition.getVersion())
            ? "1" : definition.getVersion().trim();
        return flowCode.replaceAll("[\\\\/:*?\"<>|\\s\\p{Cntrl}]", "_") + "_" + version + ".json";
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
        vo.setDefConfig(d.getDefConfig());
        vo.setCreateTime(d.getCreateTime());
        vo.setUpdateTime(d.getUpdateTime());
        return vo;
    }
}
