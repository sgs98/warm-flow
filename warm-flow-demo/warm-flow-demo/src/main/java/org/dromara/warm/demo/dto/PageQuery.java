package org.dromara.warm.demo.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

/**
 * 分页查询参数。
 * <p>页码和每页条数为空或非法时使用默认值，并限制每页最大返回 100 条。</p>
 *
 * @author may
 * @since 2026/9/11
 */
@Data
public class PageQuery {

    /**
     * 默认页码。
     */
    public static final int DEFAULT_PAGE_NUM = 1;

    /**
     * 默认每页条数。
     */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 每页最大条数。
     */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * 当前页码。
     */
    private Integer pageNum = DEFAULT_PAGE_NUM;

    /**
     * 每页条数。
     */
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    /**
     * 获取修正后的页码。
     *
     * @return 页码
     */
    public int normalizedPageNum() {
        return pageNum == null || pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
    }

    /**
     * 获取修正后的每页条数。
     *
     * @return 每页条数
     */
    public int normalizedPageSize() {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * 构建 MyBatis-Plus 分页对象。
     *
     * @param <T> 记录类型
     * @return MyBatis-Plus 分页对象
     */
    public <T> Page<T> build() {
        return new Page<>(normalizedPageNum(), normalizedPageSize());
    }

    /**
     * 构建 Warm-Flow 引擎分页对象。
     *
     * @param <T>        记录类型
     * @param sortField  排序字段
     * @param sortMethod 排序方式
     * @return Warm-Flow 引擎分页对象
     */
    public <T> org.dromara.warm.flow.core.utils.page.Page<T> build(String sortField, String sortMethod) {
        return new org.dromara.warm.flow.core.utils.page.Page<>(
            normalizedPageNum(), normalizedPageSize(), sortField, sortMethod);
    }
}
