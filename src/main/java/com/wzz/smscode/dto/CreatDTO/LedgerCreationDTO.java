package com.wzz.smscode.dto.CreatDTO;

import com.wzz.smscode.enums.FundType;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class LedgerCreationDTO {
    /**
     * 用户id
     */
    private Long userId;
    /**
     * 变动金额，始终为正数。
     * 该字段用于表示用户账户中资金的变动量，无论是入账还是出账操作，这里的值都以正数形式存储。
     * 具体的操作类型（如入账或出账）由其他字段标识。
     */
    private BigDecimal amount;
    /**
     * 账本类型 (1-入账, 0-出账)
     */
    private Integer ledgerType = 0;
    /**
     * 资金业务类型
     */
    private FundType fundType;
    /**
     *备注
     */
    private String remark;

    /**
     * 项目id
      */
    private String projectId;
    /**
     * 线路id
     */
    private Integer lineId;
    /**
     * 手机号
     */
    private String phoneNumber;
    /**
     * 验证码
     */
    private String code;
}