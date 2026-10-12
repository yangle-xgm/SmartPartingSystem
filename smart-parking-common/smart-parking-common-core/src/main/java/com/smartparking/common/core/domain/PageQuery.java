package com.smartparking.common.core.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 分页查询基类
 */
@Data
public class PageQuery implements Serializable {

    /** 页码（默认1） */
    private Long current = 1L;

    /** 每页大小（默认10） */
    private Long size = 10L;
}
