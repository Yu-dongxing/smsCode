package com.wzz.smscode.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AgentProjectLineUpdateDTO {
    /**
     * 用户项目线路对应表的主键ID (user_project_line.id)
     * 这个是必须的，用来定位要更新哪条记录
     */
    @NotNull(message = "项目配置ID不能为空")
    private Long userProjectLineId;

    /**
     * 新的代理售价 (可选)
     */
    @DecimalMin(value = "0.00", message = "价格不能为负数")
    private BigDecimal agentPrice;

    /**
     * 项目id
     */
    private String projectId;
    /**
     * 线路id
     */
    private String lineId;

    /**
     * 新的备注 (可选)
     * 允许为空字符串 "" 来清空备注
     */
    @Size(max = 255, message = "备注内容不能超过255个字符")
    private String remark;
}
