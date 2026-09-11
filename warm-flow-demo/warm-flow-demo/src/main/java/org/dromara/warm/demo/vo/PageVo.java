package org.dromara.warm.demo.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 通用分页响应 VO。
 *
 * @author may
 * @since 2026/9/5
 */
@Getter
@Setter
public class PageVo<T> {

    /**
     * 总记录数。
     */
    private long total;

    /**
     * 当前页码。
     */
    private int pageNum;

    /**
     * 每页记录数。
     */
    private int pageSize;

    /**
     * 当前页数据。
     */
    private List<T> list;

    /**
     * 默认构造方法。
     */
    public PageVo() {
    }

    /**
     * 全参构造方法。
     *
     * @param total    总记录数
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @param list     当前页数据
     */
    public PageVo(long total, int pageNum, int pageSize, List<T> list) {
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.list = list;
    }
}
