package com.zxsh.dto;

import lombok.Data;

import java.util.List;

//滚动分页查询结果
@Data
public class ScrollResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
