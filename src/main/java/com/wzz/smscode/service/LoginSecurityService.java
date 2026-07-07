package com.wzz.smscode.service;

import com.wzz.smscode.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
public class LoginSecurityService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 校验当前 IP 是否被临时封禁
     */
    public void checkIpBlocked(String ip) {
        String blockKey = "smscode:login:blocked_ip:" + ip;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(blockKey))) {
            throw new BusinessException("您的IP由于连续输错密码已被封禁，请24小时后再试");
        }
    }

    /**
     * 校验当前账号是否被锁定
     */
    public void checkAccountLocked(String username) {
        String blockKey = "smscode:login:blocked_user:" + username;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(blockKey))) {
            throw new BusinessException("该账号调用频繁，已锁定，请1小时后再试");
        }
    }

    /**
     * 账号接口限流：同一个账户 10 分钟内调用登录接口超过 10 次，锁定 1 小时
     */
    public void recordLoginCall(String username) {
        String rateKey = "smscode:login:rate_limit:user:" + username;
        String blockKey = "smscode:login:blocked_user:" + username;

        Long count = redisTemplate.opsForValue().increment(rateKey);
        if (count != null && count == 1) {
            redisTemplate.expire(rateKey, 10, TimeUnit.MINUTES);
        }

        if (count != null && count > 10) {
            redisTemplate.opsForValue().set(blockKey, "1", 1, TimeUnit.HOURS);
            throw new BusinessException("该账号调用频繁，已被系统安全锁定，请1小时后再试");
        }
    }

    /**
     * 登录失败记录：24小时内输错2次密码，封禁 IP 24小时
     */
    public void recordIpFailure(String ip) {
        String failKey = "smscode:login:failed_ip:" + ip;
        String blockKey = "smscode:login:blocked_ip:" + ip;

        Long count = redisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1) {
            redisTemplate.expire(failKey, 24, TimeUnit.HOURS);
        }

        if (count != null && count >= 2) {
            redisTemplate.opsForValue().set(blockKey, "1", 24, TimeUnit.HOURS);
            throw new BusinessException("密码错误次数过多，您的IP已被临时封禁24小时");
        }
    }

    /**
     * 登录成功：清除 IP 输错计数
     */
    public void clearIpFailure(String ip) {
        String failKey = "smscode:login:failed_ip:" + ip;
        redisTemplate.delete(failKey);
    }
}