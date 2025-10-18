package com.wzz.smscode.dto.EntityDTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProjectDTO {


    /**
     * 主键id
     */
    private Long id;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 项目ID，例如 "id0001" 表示某平台
     */

    private String projectId;

    /**
     * 项目线路ID，用于区分同一项目下不同的API线路
     */

    private Integer lineId;

    /**
     * 提供服务的域名或基础URL
     */

    private String domain;

    /**
     * 获取手机号的接口路径
     */

    private String getNumberRoute;

    /**
     * 获取验证码的接口路径
     */

    private String getCodeRoute;

    /**
     * 获取验证码超时时长，单位为秒
     */

    private Integer codeTimeout;

    /**
     * 项目成本价（平台获取号码的成本）
     */

    private BigDecimal costPrice;

    /**
     * 项目允许设置的最高售价
     */

    private BigDecimal priceMax;

    /**
     * 项目允许设置的最低售价
     */
    private BigDecimal priceMin;

    /**
     * 是否开启号码筛选功能。0表示关闭，1表示开启。
     */
    private Integer enableFilter; // 使用 Integer 对应 TINYINT，便于处理 0 和 1

    /**
     * 筛选API所需的ID或密钥
     */
    private String filterId;

}
