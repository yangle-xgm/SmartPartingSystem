package com.smartparking.common.core.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果
 */
@Data
public class PageResult<T> implements Serializable {

    /** 总记录数 */
    private Long total;

    /** 当前页数据 */
    private List<T> records;

    /** 当前页码 */
    private Long current;

    /** 每页大小 */
    private Long size;

    public PageResult() {
    }

    public PageResult(Long total, List<T> records, Long current, Long size) {
        this.total = total;
        this.records = records;
        this.current = current;
        this.size = size;
    }
}
