package com.wzz.smscode.dto.EntityDTO;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wzz.smscode.annotation.ColumnComment;
import com.wzz.smscode.annotation.DefaultValue;
import com.wzz.smscode.annotation.Index;
import com.wzz.smscode.annotation.TableComment;
import com.wzz.smscode.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 用户信息传输对象
 */
@Data
public class UserDTO {

    private Long userId;
        /**
         *用户名
         */
        private String userName;


        /**
         * 用户账户余额
         */
        private BigDecimal balance;

        /**
         * 项目价格配置的 JSON 字符串。
         * 例如：{"id0001-1": 5.0, "id0002-1": 3.5}
         */
//        private String projectPrices;

    private Map<String, BigDecimal> projectPrices;

        /**
         * 用户状态（0=正常，1=冻结/禁用等）
         */
        private Integer status;

        /**
         * 最近24小时取号次数
         */
        private Integer dailyGetCount;

        /**
         * 最近24小时成功取码（获取到验证码）次数
         */
        private Integer dailyCodeCount;

        /**
         * 最近24小时回码率
         */
        private Double dailyCodeRate;

        /**
         * 总取号次数
         */
        private Integer totalGetCount;

        /**
         * 总取码次数
         */
        private Integer totalCodeCount;

        /**
         * 总回码率
         */
        private Double totalCodeRate;

        /**
         * 上级用户ID。顶级管理员该值可为空或0。
         */
        private Long parentId;

        /**
         * 是否具有代理权限。0表示普通用户，1表示代理用户。
         */
        private Boolean isAgent;
}