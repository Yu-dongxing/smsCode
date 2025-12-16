package com.wzz.smscode.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.util.Date;

@Data
public class BatchRefundQueryDTO {
    // 必须指定时间范围，防止误操作全表
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTime;

    // 可选：指定项目或线路（虽然需求说是所有，但留个口子比较好）
    private String projectId;
    private String lineId;
    /**
     * 记录状态
     */
    private Integer status;
    /**
     * 扣款状态
     */
    private Integer charged;

}