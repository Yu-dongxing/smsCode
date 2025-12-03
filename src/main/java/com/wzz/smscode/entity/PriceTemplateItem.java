package com.wzz.smscode.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wzz.smscode.annotation.ColumnComment;
import com.wzz.smscode.annotation.ForeignKey;
import com.wzz.smscode.annotation.Index;
import com.wzz.smscode.annotation.TableComment;
import com.wzz.smscode.common.BaseEntity;
import com.wzz.smscode.enums.ForeignKeyAction;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 价格模板中的具体项目线路配置项
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("price_template_item")
@TableComment("价格模板项配置表")
@ForeignKey(
        name = "fk_template_item_template_id",
        columns = {"template_id"},
        referenceEntity = PriceTemplate.class,
        referencedColumns = {"id"},
        onDelete = ForeignKeyAction.CASCADE // 删除模板时，级联删除所有配置项
)
@Index(name = "idx_template_id", columns = {"template_id"}, comment = "模板ID索引")
public class PriceTemplateItem extends BaseEntity {

    /**
     * 所属模板的ID
     */
    @ColumnComment("所属模板ID")
    @TableField("template_id") // 【修正】指定数据库列名
    private Long templateId;

    /**
     * 项目ID
     */
    @ColumnComment("项目ID")
    @TableField("project_id") // 【添加】指定数据库列名
    private Long projectId;

    /**
     * 项目名称 (冗余字段，方便查询)
     */
    @ColumnComment("项目名称")
    @TableField("project_name") // 【添加】指定数据库列名
    private String projectName;

    /**
     * 线路ID
     */
    @ColumnComment("线路ID")
    @TableField("line_id") // 【添加】指定数据库列名
    private Long lineId;

    /**
     * 价格
     */
    @ColumnComment("价格")
    @TableField("price") // 【添加】指定数据库列名
    private BigDecimal price;

    /**
     * 成本价 (可选)
     */
    @ColumnComment("成本价")
    @TableField("cost_price") // 【添加】指定数据库列名
    private BigDecimal costPrice;

    /**
     * 允许设置的最低价 (冗余字段)
     * <p>用于校验：price 不能低于 minPrice。通常 minPrice = costPrice</p>
     */
    @ColumnComment("允许最低价")
    @TableField("min_price")
    private BigDecimal minPrice;

    /**
     * 允许设置的最高价 (冗余字段)
     * <p>用于校验：price 不能高于 maxPrice。通常同步自总项目的 maxPrice</p>
     */
    @ColumnComment("允许最高价")
    @TableField("max_price")
    private BigDecimal maxPrice;

    /**
     * 项目表id
     */
    @ColumnComment("项目表id")
    @TableField("project_table_id")
    private Long projectTableId;
}