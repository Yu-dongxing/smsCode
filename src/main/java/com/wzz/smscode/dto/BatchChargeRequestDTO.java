package com.wzz.smscode.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class BatchChargeRequestDTO {
    /**
     * 目标用户ID列表
     */
    private List<Long> userIds;

    /**
     * 金额（必须为正数）
     */
    private BigDecimal amount;

    /**
     * 操作类型：true-充值，false-扣款
     */
    private Boolean isRecharge;

    /**
     * 备注（可选）
     */
    private String remark;
}