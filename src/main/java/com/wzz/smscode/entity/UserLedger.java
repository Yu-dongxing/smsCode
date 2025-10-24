package com.wzz.smscode.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.wzz.smscode.annotation.*;
import com.wzz.smscode.common.BaseEntity;
import com.wzz.smscode.enums.ForeignKeyAction;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户账本表实体类
 * <p>
 * 记录用户资金变动明细和业务消费记录。
 * </p>
 * 对应数据库表：user_ledger
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_ledger")
@TableComment("用户账本表")
@ForeignKey(
        name = "fk_ledger_user_id",
        columns = {"user_id"},
        referenceEntity = User.class,       // 引用 User 实体
        referencedColumns = {"id"},         // 引用 User 表的 id 列 (根据您的最新说明)
        onDelete = ForeignKeyAction.CASCADE,  // 用户删除时，其账本记录也一并删除，保证数据清理
        onUpdate = ForeignKeyAction.RESTRICT
)
@Index(name = "idx_user_id", columns = {"user_id"}, comment = "用户ID索引，加速查询用户流水")
@Index(name = "idx_timestamp", columns = {"timestamp"}, comment = "操作时间索引，便于按时间范围查询")
public class UserLedger extends BaseEntity {

    /**
     * 该记录所属的用户ID
     */
    @ColumnComment("用户ID")
    @TableField("user_id")
    private Long userId;

    /**
     * 项目ID（业务资金变动时填写，如取号扣款）
     */
    @ColumnComment("项目ID")
    @TableField("project_id")
    private String projectId;

    /**
     * 项目线路ID（业务资金变动时填写）
     */
    @ColumnComment("项目线路ID")
    @TableField("line_id")
    private Integer lineId;

    /**
     * 手机号码（如果是取号扣费，则记录对应的号码）
     */
    @ColumnComment("手机号码")
    @TableField("phone_number")
    private String phoneNumber;

    /**
     * 验证码（如果获取成功，则记录验证码内容）
     */
    @ColumnComment("验证码")
    @TableField("code")
    private String code;

    /**
     * 变动金额
     */
    @ColumnComment("变动金额")
    @TableField("price")
    private BigDecimal price;

    /**
     * 变动前用户余额
     */
    @ColumnComment("变动前用户余额")
    @TableField("balance_before")
    private BigDecimal balanceBefore;

    /**
     * 变动后用户余额
     */
    @ColumnComment("变动后用户余额")
    @TableField("balance_after")
    private BigDecimal balanceAfter;

    /**
     * 操作时间（扣费或充值发生的时间）
     */
    @ColumnComment("操作时间")
    @TableField("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    /**
     * 资金类型：0 表示业务扣费，1 表示上级代理或管理员的充值/扣款
     */
    @ColumnComment("资金类型（0-业务扣费, 1-后台操作）")
    @TableField("fund_type")
    private Integer fundType;

    /**
     * 账本类型（1-入账，0-出账）
     */
    @TableField("ledger_type")
    @ColumnComment("账本类型（1-入账，0-出账）")
    private Integer ledgerType;


    /**
     * 备注
     */
    @TableField("remark")
    @ColumnComment("备注")
    private  String remark;
}