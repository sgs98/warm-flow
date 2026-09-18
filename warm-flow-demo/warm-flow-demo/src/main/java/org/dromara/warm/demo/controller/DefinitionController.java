package org.dromara.warm.demo.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.warm.demo.common.ApiResponse;
import org.dromara.warm.demo.dto.PageQuery;
import org.dromara.warm.demo.service.DefinitionService;
import org.dromara.warm.demo.vo.DefinitionExportVo;
import org.dromara.warm.demo.vo.DefinitionSummaryVo;
import org.dromara.warm.demo.vo.PageVo;
import org.dromara.warm.flow.core.dto.DefJson;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * 流程定义接口。
 *
 * @author may
 * @since 2026/9/5
 */
@RestController
@RequestMapping("/api/definitions")
@RequiredArgsConstructor
public class DefinitionController {

    private final DefinitionService definitionService;

    /**
     * 分页查询流程定义。
     *
     * @param pageQuery 分页参数
     * @param keyword   流程名称或编码关键字
     * @param category  流程分类
     * @param isPublish 发布状态
     * @return 流程定义分页数据
     */
    @GetMapping
    public ApiResponse<PageVo<DefinitionSummaryVo>> page(
        PageQuery pageQuery,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) Integer isPublish) {
        return ApiResponse.ok(definitionService.page(pageQuery, keyword, category, isPublish));
    }

    /**
     * 查询流程定义设计数据。
     *
     * @param id 流程定义主键
     * @return 流程定义设计数据
     */
    @GetMapping("/{id}")
    public ApiResponse<DefJson> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(definitionService.detail(id));
    }

    /**
     * 保存流程定义设计数据。
     *
     * @param defJson 流程定义设计数据
     * @return 流程定义主键
     */
    @PostMapping
    public ApiResponse<Long> save(@RequestBody DefJson defJson) {
        return ApiResponse.ok(definitionService.save(defJson));
    }

    /**
     * 发布流程定义。
     *
     * @param id 流程定义主键
     * @return 空响应
     */
    @PutMapping("/{id}/publish")
    public ApiResponse<Void> publish(@PathVariable("id") Long id) {
        definitionService.publish(id);
        return ApiResponse.ok();
    }

    /**
     * 复制流程定义。
     *
     * @param id 流程定义主键
     * @return 新流程定义主键
     */
    @PostMapping("/{id}/copy")
    public ApiResponse<Long> copy(@PathVariable("id") Long id) {
        return ApiResponse.ok(definitionService.copy(id));
    }

    /**
     * 导出流程定义 json 文件，内容包含流程定义、流程节点与流程跳转。
     *
     * @param id 流程定义主键
     * @return 流程定义 json 文件流
     */
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable("id") Long id) {
        DefinitionExportVo exportVo = definitionService.export(id);
        ContentDisposition disposition = ContentDisposition.attachment()
            .filename(exportVo.getFileName(), StandardCharsets.UTF_8)
            .build();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(exportVo.getContent().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 导入流程定义 json 文件。
     *
     * @param file 流程定义 json 文件
     * @return 新流程定义主键
     */
    @PostMapping("/import")
    public ApiResponse<Long> importFile(@RequestParam(value = "file", required = false) MultipartFile file) {
        return ApiResponse.ok(definitionService.importFile(file));
    }

    /**
     * 删除流程定义。
     *
     * @param id 流程定义主键
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(@PathVariable("id") Long id) {
        definitionService.remove(id);
        return ApiResponse.ok();
    }
}
