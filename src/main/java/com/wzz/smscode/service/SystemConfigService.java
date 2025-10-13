package com.wzz.smscode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wzz.smscode.entity.SystemConfig;
import com.wzz.smscode.entity.User;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

public interface SystemConfigService extends IService<SystemConfig> {
    //    @Cacheable(value = "systemConfig", key = "'" + CONFIG_ID + "'")
    SystemConfig getConfig();

    @Transactional
//    @CacheEvict(value = "systemConfig", key = "'" + CONFIG_ID + "'")
    boolean updateConfig(SystemConfig config);

    boolean isBanModeEnabled();

    Double getMinCodeRateLimit();

    BigDecimal getBalanceThreshold();

    void checkAndBanUser(User user);
}
