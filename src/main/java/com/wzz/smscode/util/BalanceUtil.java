package com.wzz.smscode.util;

import com.wzz.smscode.entity.User; // 假设这是你的用户实体类
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 余额与统计相关业务逻辑计算工具类
 */
public final class BalanceUtil {

    // 余额封控阈值，例如 5.00 元
    private static final BigDecimal BALANCE_THRESHOLD = new BigDecimal("5.00");

    private BalanceUtil() {
        // 防止实例化
    }

    /**
     * 判断用户是否满足取号条件
     * Service层应先查询数据库判断 hasOngoingRecord，再调用此方法
     *
     * @param user               用户实体
     * @param hasOngoingRecord   该用户当前是否有正在进行的取码任务
     * @return 如果可以取号，返回 true
     */
    public static boolean canGetNumber(User user, boolean hasOngoingRecord) {
        if (user == null || user.getStatus() != 0) {
            return false; // 用户不存在或被禁用
        }

        // 检查余额是否低于阈值
        if (user.getBalance().compareTo(BALANCE_THRESHOLD) <= 0) {
            // 余额低于或等于阈值，且当前有正在进行的任务，则禁止继续取号
            if (hasOngoingRecord) {
                return false;
            }
        }
        return true;
    }

    /**
     * 更新用户的统计数据（内存中的对象，不执行DB操作）
     * Service层调用此方法修改实体后，再持久化到数据库
     *
     * @param user           要更新的用户实体
     * @param codeReceived   本次操作是否成功获取到了验证码
     */
    public static void updateStats(User user, boolean codeReceived) {
        if (user == null) return;

        // 更新日统计
        user.setDailyGetCount(user.getDailyGetCount() + 1);

        // 更新总统计
        user.setTotalGetCount(user.getTotalGetCount() + 1);

        if (codeReceived) {
            user.setDailyCodeCount(user.getDailyCodeCount() + 1);
            user.setTotalCodeCount(user.getTotalCodeCount() + 1);
        }

        // 重新计算回码率，避免除零错误
        if (user.getDailyGetCount() > 0) {
            double dailyRate = (double) user.getDailyCodeCount() / user.getDailyGetCount();
            user.setDailyCodeRate(dailyRate);
        } else {
            user.setDailyCodeRate(0.0);
        }

        if (user.getTotalGetCount() > 0) {
            double totalRate = (double) user.getTotalCodeCount() / user.getTotalGetCount();
            user.setTotalCodeRate(totalRate);
        } else {
            user.setTotalCodeRate(0.0);
        }
    }

    /**
     * 重置用户的日统计数据（内存中的对象）
     *
     * @param user 要重置的用户实体
     */
    public static void resetDailyStats(User user) {
        if (user == null) return;
        user.setDailyGetCount(0);
        user.setDailyCodeCount(0);
        user.setDailyCodeRate(0.0);
    }
}