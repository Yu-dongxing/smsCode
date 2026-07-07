package com.wzz.smscode.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.wzz.smscode.annotation.TableComment;
import com.wzz.smscode.common.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Data
@TableName("user_project_ban")
@TableComment("临时封禁实体类")
public class UserProjectBan  extends BaseEntity {


    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 项目id
     */
    @TableField("project_id")
    private String projectId;

    /**
     * 线路ID
     */
    @TableField("line_id")
    private Integer lineId;

    // ============== 新增触发快照字段 ==============

    /**
     * 触发封禁时的取号数
     */
    @TableField("trigger_attempts")
    private Integer triggerAttempts;

    /**
     * 触发封禁时的成功接码数
     */
    @TableField("trigger_successes")
    private Integer triggerSuccesses;

    /**
     * 触发封禁时的来码率
     */
    @TableField("trigger_rate")
    private BigDecimal triggerRate;

    /**
     *
     */
    @TableField("ban_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime banTime;

    @TableField("unban_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime unbanTime;

    @TableField("status")
    private Integer status; // 0-封禁中, 1-已解禁
}