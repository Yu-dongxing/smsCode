package com.wzz.smscode.dto;

import lombok.Data;
import java.util.Date;

/**
 * 号码记录传输对象
 */
@Data
public class NumberDTO {

    private String phoneNumber;
    private String code;
    /**
     * 状态（例如：0=已取号, 1=已取码, 2=已释放, 3=超时）
     */
    private Integer status;
    private Date getNumberTime;
    private Date codeReceivedTime;
}