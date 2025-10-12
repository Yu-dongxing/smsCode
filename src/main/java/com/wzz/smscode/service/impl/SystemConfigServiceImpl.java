package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wzz.smscode.entity.SystemConfig;
import com.wzz.smscode.mapper.SystemConfigMapper;
import com.wzz.smscode.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SystemConfigServiceImpl extends ServiceImpl<SystemConfigMapper, SystemConfig> implements SystemConfigService {
    @Autowired
    private SystemConfigMapper systemConfigMapper;
}
