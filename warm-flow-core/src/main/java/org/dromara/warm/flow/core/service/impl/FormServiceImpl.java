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
package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Form;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.orm.dao.FlowFormDao;
import org.dromara.warm.flow.core.orm.service.impl.WarmServiceImpl;
import org.dromara.warm.flow.core.service.FormService;
import org.dromara.warm.flow.core.utils.AssertUtil;
import org.dromara.warm.flow.core.utils.ClassUtil;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.ObjectUtil;
import org.dromara.warm.flow.core.utils.page.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 流程表单服务实现。
 *
 * <p>负责表单版本生成、发布状态切换、复制以及已发布表单查询，并在取消发布前校验流程定义和节点引用。</p>
 *
 * @author vanlin
 * @since 2024/8/19 10:07
 */
public class FormServiceImpl extends WarmServiceImpl<FlowFormDao<Form>, Form> implements FormService {

    /**
     * 表单版本解析异常日志。
     */
    public static final Logger LOGGER = LoggerFactory.getLogger(FormServiceImpl.class);

    /**
     * 注入表单 DAO。
     *
     * @param warmDao 表单数据访问对象
     * @return 当前服务实例
     */
    @Override
    public FormService setDao(FlowFormDao<Form> warmDao) {
        this.warmDao = warmDao;
        return this;
    }

    /**
     * 发布指定表单。
     *
     * @param id 表单主键
     * @return 是否更新成功
     */
    @Override
    public boolean publish(Long id) {
        Form form = getById(id);
        AssertUtil.isTrue(form.getIsPublish().equals(PublishStatus.PUBLISHED.getKey()), ExceptionCons.FORM_ALREADY_PUBLISH);
        form.setIsPublish(PublishStatus.PUBLISHED.getKey());
        return updateById(form);
    }

    /**
     * 取消发布指定表单。
     *
     * <p>流程节点或流程定义仍引用该表单时禁止取消发布。</p>
     *
     * @param id 表单主键
     * @return 是否更新成功
     */
    @Override
    public boolean unPublish(Long id) {
        Form form = getById(id);
        List<Node> nodes = FlowEngine.nodeService().list(FlowEngine.newNode().setFormPath("" + form.getId()));
        AssertUtil.isNotEmpty(nodes, ExceptionCons.EXIST_USE_FORM);
        List<Definition> definitions = FlowEngine.defService().list(FlowEngine.newDef().setFormPath("" + form.getId()));
        AssertUtil.isNotEmpty(definitions, ExceptionCons.EXIST_USE_FORM);
        AssertUtil.isTrue(form.getIsPublish().equals(PublishStatus.UNPUBLISHED.getKey()), ExceptionCons.FORM_ALREADY_UN_PUBLISH);
        form.setIsPublish(PublishStatus.UNPUBLISHED.getKey());
        return updateById(form);
    }

    /**
     * 保存表单，并按相同表单编码的历史数据生成新版本号。
     *
     * @param form 待保存表单
     * @return 是否保存成功
     */
    @Override
    public boolean save(Form form) {
        form.setVersion(getNewVersion(form));
        return super.save(form);
    }

    /**
     * 复制表单为一个新的未发布版本。
     *
     * @param id 源表单主键
     * @return 是否保存成功
     */
    @Override
    public boolean copyForm(Long id) {
        Form form = ClassUtil.clone(getById(id));
        AssertUtil.isTrue(ObjectUtil.isNull(form), ExceptionCons.NOT_FOUNT_DEF);
        FlowEngine.dataFillHandler().idFill(form.setId(null));
        form.setVersion(getNewVersion(form))
            .setIsPublish(PublishStatus.UNPUBLISHED.getKey())
            .setCreateTime(null)
            .setUpdateTime(null);
        return save(form);
    }

    /**
     * 按表单编码和版本查询唯一表单。
     *
     * @param formCode    表单编码
     * @param formVersion 表单版本
     * @return 唯一匹配的表单
     */
    @Override
    public Form getByCode(String formCode, String formVersion) {
        List<Form> list = list(FlowEngine.newForm().setFormCode(formCode).setVersion(formVersion));
        AssertUtil.isTrue(CollUtil.isEmpty(list), ExceptionCons.NOT_FOUNT_TASK);
        AssertUtil.isTrue(list.size() > 1, ExceptionCons.FORM_NOT_ONE);
        return list.get(0);
    }

    /**
     * 按主键查询表单，并统一校验主键非空。
     *
     * @param id 表单主键
     * @return 表单数据
     */
    @Override
    public Form getById(Long id) {
        AssertUtil.isNull(id, ExceptionCons.ID_EMPTY);
        return super.getById(id);
    }

    /**
     * 分页查询已发布表单。
     *
     * @param formName 表单名称筛选条件
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 已发布表单分页数据
     */
    @Override
    public Page<Form> publishedPage(String formName, Integer pageNum, Integer pageSize) {
        return page(FlowEngine.newForm().setFormName(formName).setIsPublish(1),
            Page.<Form>pageOf(pageNum, pageSize));
    }

    /**
     * 保存未发布表单的设计内容。
     *
     * @param id          表单主键
     * @param formContent 表单设计内容
     * @return 是否更新成功
     */
    @Override
    public boolean saveContent(Long id, String formContent) {
        Form form = getById(id);
        AssertUtil.isTrue(form.getIsPublish().equals(PublishStatus.PUBLISHED.getKey()), ExceptionCons.FORM_ALREADY_PUBLISH);

        form.setFormContent(formContent);
        return updateById(form);
    }

    /**
     * 计算同一表单编码的下一个正整数版本号，无法解析的历史版本不参与递增。
     *
     * @param form 待保存表单
     * @return 新版本号
     */
    private String getNewVersion(Form form) {
        List<String> formCodeList = List.of(form.getFormCode());
        List<Form> forms = getDao().queryByCodeList(formCodeList);
        int highestVersion = 0;

        for (Form otherForm : forms) {
            if (form.getFormCode().equals(otherForm.getFormCode())) {
                try {
                    int version = Integer.parseInt(otherForm.getVersion());
                    if (version > highestVersion) {
                        highestVersion = version;
                    }
                } catch (NumberFormatException e) {
                    LOGGER.error("版本格式化异常 - {}", e.getLocalizedMessage());
                }
            }
        }

        String version = "1";
        if (highestVersion > 0) {
            version = String.valueOf(highestVersion + 1);
        }

        return version;
    }
}
