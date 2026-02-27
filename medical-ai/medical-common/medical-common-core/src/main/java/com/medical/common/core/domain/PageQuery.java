package com.medical.common.core.domain;

import lombok.Data;

@Data
public class PageQuery {
    /** 页码，从1开始 */
    private Integer pageNum = 1;
    /** 每页数量 */
    private Integer pageSize = 10;
    /** 排序字段 */
    private String orderBy;
    /** 排序方向 asc/desc */
    private String orderDirection = "desc";
}
