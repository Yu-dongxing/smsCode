package com.wzz.smscode.service;

import com.wzz.smscode.entity.Project;
import com.wzz.smscode.entity.User;

import java.math.BigDecimal;

public interface PriceSyncService {

    void validateProjectPriceConfig(Project project);

    void syncByProjectChanged(Project project);

    void syncByTemplateChanged(Long templateId);

    void validateRuntimeParentPrice(User user, String projectId, Integer lineId, BigDecimal currentPrice);
}
