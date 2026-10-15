package org.dromara.warm.demo.service;

import org.dromara.warm.demo.dto.PageQuery;
import org.dromara.warm.demo.vo.DefinitionExportVo;
import org.dromara.warm.demo.vo.DefinitionSummaryVo;
import org.dromara.warm.demo.vo.PageVo;
import org.dromara.warm.flow.core.dto.DefJson;
import org.springframework.web.multipart.MultipartFile;

/**
 * 流程定义业务接口。
 *
 * @author may
 * @since 2026/9/11
 */
public interface DefinitionService {

    /**
     * 分页查询流程定义。
     *
     * @param pageQuery 分页参数
     * @param keyword   流程编码或名称关键字
     * @param category  流程分类
     * @param isPublish 发布状态
     * @return 流程定义分页数据
     */
    PageVo<DefinitionSummaryVo> page(PageQuery pageQuery, String keyword,
                                     String category, Integer isPublish);

    /**
     * 查询流程定义的设计器数据。
     *
     * @param id 流程定义主键
     * @return 流程定义设计数据
     */
    DefJson detail(Long id);

    /**
     * 保存流程定义设计数据。
     *
     * @param defJson 流程定义设计数据
     * @return 流程定义主键
     */
    Long save(DefJson defJson);

    /**
     * 发布流程定义。
     *
     * @param id 流程定义主键
     */
    void publish(Long id);

    /**
     * 复制流程定义。
     *
     * @param id 原流程定义主键
     * @return 新流程定义主键
     */
    Long copy(Long id);

    /**
     * 更新流程定义的自动审批开关（定义配置 {@code def_config} 中的 {@code autoPass} 键）。
     *
     * <p>自动审批是定义级的运行时开关，引擎在每次办理时重新读取定义，因此已发布版本也可修改，
     * 改完对后续办理立即生效。只写 {@code autoPass} 一个键，业务扩展属性 {@code ext} 不受影响。</p>
     *
     * @param id       流程定义主键
     * @param autoPass 是否开启同一办理人自动审批
     */
    void updateAutoPass(Long id, Boolean autoPass);

    /**
     * 导出流程定义（含流程节点与流程跳转），由 Controller 输出为 json 文件下载。
     *
     * @param id 流程定义主键
     * @return 导出文件名与流程定义 json 内容
     */
    DefinitionExportVo export(Long id);

    /**
     * 导入流程定义 json 文件，文件需为导出得到的流程定义文件。
     * <p>由引擎 importIs 完成解析与结构校验，导入后作为新的流程定义版本落库，不覆盖本地已有定义。</p>
     *
     * @param file 流程定义 json 文件
     * @return 新流程定义主键
     */
    Long importFile(MultipartFile file);

    /**
     * 删除流程定义。
     *
     * @param id 流程定义主键
     */
    void remove(Long id);
}
