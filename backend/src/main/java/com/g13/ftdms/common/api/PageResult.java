package com.g13.ftdms.common.api;

import java.util.List;
import java.util.function.Function;

/**
 * 分页结果包装（用于 C1 动作库、D1 食物库等分页查询接口）。
 *
 * <p>与前端约定字段：{@code list / page / size / total}。
 * 页码从 1 开始；{@code total} 为满足条件的总记录数，用于前端计算总页数。</p>
 *
 * @param <T> 列表元素类型
 */
public class PageResult<T> {

    /** 当前页数据 */
    private List<T> list;
    /** 当前页码（1 起） */
    private int page;
    /** 每页条数 */
    private int size;
    /** 满足条件的总记录数 */
    private long total;

    public PageResult() {
    }

    public PageResult(List<T> list, int page, int size, long total) {
        this.list = list;
        this.page = page;
        this.size = size;
        this.total = total;
    }

    /**
     * 由 Spring Data 的 Page 对象转换而来，并把实体映射为响应 DTO。
     *
     * @param page Spring Data 分页对象
     * @param mapper 实体 → DTO 的转换函数
     * @param <E> 实体类型
     * @param <T> DTO 类型
     * @return 统一分页结构
     */
    public static <E, T> PageResult<T> of(org.springframework.data.domain.Page<E> page, Function<E, T> mapper) {
        List<T> mapped = page.getContent().stream().map(mapper).toList();
        return new PageResult<>(mapped, page.getNumber() + 1, page.getSize(), page.getTotalElements());
    }

    /**
     * 由已就绪的列表构造（列表查询不做 SQL 分页时使用）。
     *
     * @param list  当前页数据
     * @param page  页码
     * @param size  每页条数
     * @param total 总数
     */
    public static <T> PageResult<T> of(List<T> list, int page, int size, long total) {
        return new PageResult<>(list, page, size, total);
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }
}
