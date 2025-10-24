package com.wzz.smscode.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 号码记录传输对象
 */
@Data
public class NumberDTO {

    /**
     * 手机号
     */
    private String phoneNumber;
    /**
     * 验证码
     */
    private String code;
    /**
     * 状态（例如：0=已取号, 1=已取码, 2=已释放, 3=超时）
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 错误信息
     */
    private String errorInfo;
    /**
     * 获取手机号的时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime getNumberTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime codeReceivedTime;
}