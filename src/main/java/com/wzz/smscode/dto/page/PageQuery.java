package com.wzz.smscode.dto.page;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 通用分页查询参数对象
 */
@Data
public class PageQuery {

    @Min(value = 1, message = "页码不能小于1")
    private long current = 1L; // 默认查询第一页

    @Min(value = 1, message = "每页数量不能小于1")
    @Max(value = 100, message = "每页数量不能超过100") // 设置一个最大值，防止恶意查询
    private long size = 10L;   // 默认每页10条
}