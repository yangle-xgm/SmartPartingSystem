package com.smartparking.common.core.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 分页查询基类
 *
 * <p>报告 P2-15：原先 size 无上限、current 无下限，前端传 {@code size=100000}
 * 即可一次性拖走整表（也是慢查询/内存打爆的入口）。这里在 setter 上做钳制：
 * current 最小 1，size 夹在 1..200 之间，所有继承本类的查询对象自动受保护。
 */
@Data
public class PageQuery implements Serializable {

    /** 单页最大条数：足够覆盖列表/导出场景，又不至于把整表拉进内存 */
    public static final long MAX_PAGE_SIZE = 200L;

    /** 页码（默认1，最小1） */
    private Long current = 1L;

    /** 每页大小（默认10，最大200） */
    private Long size = 10L;

    public Long getCurrent() {
        return current == null || current < 1 ? 1L : current;
    }

    public void setCurrent(Long current) {
        this.current = current;
    }

    public Long getSize() {
        if (size == null || size < 1) {
            return 10L;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    public void setSize(Long size) {
        this.size = size;
    }
}
