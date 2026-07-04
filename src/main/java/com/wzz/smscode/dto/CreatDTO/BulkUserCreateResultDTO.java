package com.wzz.smscode.dto.CreatDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkUserCreateResultDTO {
    /**
     * 新生成的用户名
     */
    private String username;
    /**
     * 明文密码
     */
    private String password;
    /**
     * 初始余额
     */
    private BigDecimal balance;
    /**
     * 归属上级ID
     */
    private Long parentId;
    /**
     * 归属上级名称
     */
    private String parentName;
    /**
     * 绑定的价格模板ID
     */
    private Long templateId;
    /**
     * 绑定的价格模板名称
     */
    private String templateName;
}