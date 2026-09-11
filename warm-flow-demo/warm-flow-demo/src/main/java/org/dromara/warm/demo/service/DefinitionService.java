package org.dromara.warm.demo.service;

import org.dromara.warm.demo.vo.DefinitionSummaryVo;
import org.dromara.warm.demo.vo.PageVo;
import org.dromara.warm.flow.core.dto.DefJson;

/**
 * 流程定义业务接口。
 *
 * @author may
 * @since 2026/9/11
 */
public interface DefinitionService {

    PageVo<DefinitionSummaryVo> page(Integer pageNum, Integer pageSize, String keyword,
                                     String category, Integer isPublish);

    DefJson detail(Long id);

    Long save(DefJson defJson);

    void publish(Long id);

    Long copy(Long id);

    void remove(Long id);
}
