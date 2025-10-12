package com.wzz.smscode.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 账本记录传输对象
 */
@Data
public class LedgerDTO {

    private String projectId;
    private Integer lineId;
    private String phoneNumber;
    private String code;
    private BigDecimal price;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private Date timestamp;
    /**
     * 资金类型（例如：0=业务扣费, 1=代理充值, 2=代理扣款）
     */
    private Integer fundType;
}