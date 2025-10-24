package com.wzz.smscode.dto.EntityDTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账本记录传输对象
 */
@Data
public class LedgerDTO {

    /**
     * id
     */
    private Long id;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 项目ID（业务资金变动时填写，如取号扣款）
     */
    private String projectId;

    /**
     * 项目线路ID（业务资金变动时填写）
     */
    private Integer lineId;

    /**
     * 手机号码（如果是取号扣费，则记录对应的号码）
     */
    private String phoneNumber;

    /**
     * 验证码（如果获取成功，则记录验证码内容）
     */
    private String code;
    /**
     * 金额。正数表示支出（扣款），负数表示收入（充值或退款）。
     */
    private BigDecimal price;

    /**
     * 变动前用户余额
     */
    private BigDecimal balanceBefore;

    /**
     * 变动后用户余额
     */
    private BigDecimal balanceAfter;

    /**
     * 操作时间（扣费或充值发生的时间）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    /**
     * 资金类型：0 表示业务扣费，1 表示上级代理或管理员的充值/扣款
     */
    private Integer fundType;

    /**
     * 账本类型（1-入账，0-出账）
     */


    /**
     * 备注
     */
    private  String remark;
}