package com.medical.common.core.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PageResult<T> implements Serializable {
    /** 总记录数 */
    private long total;
    /** 当前页数据 */
    private List<T> records;
    /** 当前页码 */
    private int pageNum;
    /** 每页数量 */
    private int pageSize;

    public static <T> PageResult<T> of(List<T> records, long total, int pageNum, int pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        return result;
    }
}
