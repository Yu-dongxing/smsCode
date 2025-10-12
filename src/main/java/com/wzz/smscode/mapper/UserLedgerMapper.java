package com.wzz.smscode.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wzz.smscode.entity.UserLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface UserLedgerMapper extends BaseMapper<UserLedger> {
    /**
     * 根据用户ID计算其账本中所有金额的总和
     * @param userId 用户ID
     * @return 理论余额
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM user_ledger WHERE user_id = #{userId}")
    BigDecimal sumAmountByUserId(Long userId);
}
