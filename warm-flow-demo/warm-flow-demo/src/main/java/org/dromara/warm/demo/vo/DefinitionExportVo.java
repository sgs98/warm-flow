package org.dromara.warm.demo.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 流程定义导出 VO，内容为引擎标准导出 json，由 Controller 输出为 json 文件下载。
 *
 * @author may
 * @since 2026/9/18
 */
@Getter
@Setter
public class DefinitionExportVo {

    /**
     * 建议的导出文件名，形如 流程编码_版本号.json。
     */
    private String fileName;

    /**
     * 流程定义导出内容（流程定义、流程节点和流程跳转的 json 字符串）。
     */
    private String content;
}
