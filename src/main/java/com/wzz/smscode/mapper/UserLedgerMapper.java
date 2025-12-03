package com.wzz.smscode.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wzz.smscode.entity.User;
import com.wzz.smscode.entity.UserLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface UserLedgerMapper extends BaseMapper<UserLedger> {
    /**
     * 根据用户ID计算其账本中所有金额的总和（带排他锁）
     *
     * 注意：
     * 1. 必须在 @Transactional 事务中使用才有效。
     * 2. 这会锁定该用户的所有账本记录，可能会阻塞该用户的充值、扣费等写入操作。
     *
     * @param userId 用户ID
     * @return 理论余额
     */
    @Select("SELECT COALESCE(SUM(CASE WHEN ledger_type = 1 THEN price ELSE -price END), 0)" +
            "    FROM user_ledger" +
            "    WHERE user_id = #{userId} " +
            "    FOR UPDATE")
    BigDecimal sumAmountByUserId(Long userId);

//    // 核心代码：添加 FOR UPDATE
//    // 这条 SQL 会锁定 id 对应的行，直到事务结束（提交或回滚）
//    @Select("SELECT * FROM user WHERE id = #{id} FOR UPDATE")
//    User selectByIdForUpdate(@Param("id") Long id);
}
