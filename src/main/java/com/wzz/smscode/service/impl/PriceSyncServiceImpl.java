package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzz.smscode.entity.PriceTemplate;
import com.wzz.smscode.entity.PriceTemplateItem;
import com.wzz.smscode.entity.Project;
import com.wzz.smscode.entity.User;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.service.PriceSyncService;
import com.wzz.smscode.service.PriceTemplateItemService;
import com.wzz.smscode.service.PriceTemplateService;
import com.wzz.smscode.service.ProjectService;
import com.wzz.smscode.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
public class PriceSyncServiceImpl implements PriceSyncService {

    @Autowired @Lazy private PriceTemplateService priceTemplateService;
    @Autowired @Lazy private PriceTemplateItemService priceTemplateItemService;
    @Autowired @Lazy private ProjectService projectService;
    @Autowired @Lazy private UserService userService;

    @Override
    public void validateProjectPriceConfig(Project project) {
        if (project == null) {
            throw new BusinessException("项目配置不能为空");
        }
        BigDecimal costPrice = zeroIfNull(project.getCostPrice());
        BigDecimal minPrice = project.getPriceMin() == null ? costPrice : project.getPriceMin();
        BigDecimal maxPrice = project.getPriceMax();

        if (costPrice.compareTo(BigDecimal.ZERO) < 0 || minPrice.compareTo(BigDecimal.ZERO) < 0
                || (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) < 0)) {
            throw new BusinessException("成本价、最低价、最高价不能小于0");
        }
        if (minPrice.compareTo(costPrice) < 0) {
            throw new BusinessException("最低价不能低于成本价");
        }
        if (hasMax(maxPrice) && maxPrice.compareTo(minPrice) < 0) {
            throw new BusinessException("最高价不能低于最低价");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void syncByProjectChanged(Project project) {
        validateProjectPriceConfig(project);

        String projectId = project.getProjectId();
        Long lineId = Long.valueOf(project.getLineId());
        List<PriceTemplateItem> items = priceTemplateItemService.list(new LambdaQueryWrapper<PriceTemplateItem>()
                .and(wrapper -> wrapper.eq(PriceTemplateItem::getProjectTableId, project.getId())
                        .or(w -> w.eq(PriceTemplateItem::getProjectId, Long.valueOf(projectId))
                                .eq(PriceTemplateItem::getLineId, lineId))));
        if (CollectionUtils.isEmpty(items)) {
            return;
        }

        Set<String> syncedKeys = new HashSet<>();
        List<PriceTemplate> rootTemplates = priceTemplateService.list(new LambdaQueryWrapper<PriceTemplate>()
                .eq(PriceTemplate::getCreatId, 0L));
        for (PriceTemplate rootTemplate : rootTemplates) {
            PriceTemplateItem rootItem = getItem(rootTemplate.getId(), project);
            if (rootItem == null) {
                continue;
            }
            applyProjectBase(rootItem, project);
            priceTemplateItemService.updateById(rootItem);
            syncedKeys.add(syncKey(rootTemplate.getId(), project));
            syncChildren(rootTemplate.getId(), rootItem, project, syncedKeys);
        }

        // Repair orphaned or irregular templates too, so project metadata never stays stale.
        for (PriceTemplateItem item : items) {
            if (syncedKeys.contains(syncKey(item.getTemplateId(), project))) {
                continue;
            }
            PriceTemplate template = priceTemplateService.getById(item.getTemplateId());
            BigDecimal floorPrice = resolveCreatorCost(template, projectId, lineId);
            applyTemplateBounds(item, project, floorPrice);
            priceTemplateItemService.updateById(item);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void syncByTemplateChanged(Long templateId) {
        if (templateId == null) {
            return;
        }
        List<PriceTemplateItem> parentItems = priceTemplateItemService.list(new LambdaQueryWrapper<PriceTemplateItem>()
                .eq(PriceTemplateItem::getTemplateId, templateId));
        if (CollectionUtils.isEmpty(parentItems)) {
            return;
        }
        Set<String> syncedKeys = new HashSet<>();
        for (PriceTemplateItem parentItem : parentItems) {
            Project project = projectService.getById(parentItem.getProjectTableId());
            if (project == null) {
                project = projectService.getProject(String.valueOf(parentItem.getProjectId()), parentItem.getLineId().intValue());
            }
            if (project == null) {
                log.warn("价格同步跳过：模板项 {} 找不到项目配置", parentItem.getId());
                continue;
            }
            validateProjectPriceConfig(project);
            syncedKeys.add(syncKey(templateId, project));
            syncChildren(templateId, parentItem, project, syncedKeys);
        }
    }

    @Override
    public void validateRuntimeParentPrice(User user, String projectId, Integer lineId, BigDecimal currentPrice) {
        if (user == null || currentPrice == null || user.getParentId() == null || user.getParentId() == 0L) {
            return;
        }
        User parent = userService.getById(user.getParentId());
        if (parent == null || parent.getTemplateId() == null) {
            return;
        }
        PriceTemplateItem parentItem = priceTemplateService.getPriceConfig(parent.getTemplateId(), projectId, lineId);
        if (parentItem == null || parentItem.getPrice() == null) {
            return;
        }
        if (currentPrice.compareTo(parentItem.getPrice()) < 0) {
            throw new BusinessException("价格配置低于上级成本，请联系上级调整模板");
        }
    }

    private void syncChildren(Long parentTemplateId, PriceTemplateItem parentItem, Project project, Set<String> syncedKeys) {
        if (parentTemplateId == null || parentItem == null || parentItem.getPrice() == null) {
            return;
        }
        List<User> boundUsers = userService.list(new LambdaQueryWrapper<User>()
                .eq(User::getTemplateId, parentTemplateId)
                .select(User::getId, User::getIsAgent));
        if (CollectionUtils.isEmpty(boundUsers)) {
            return;
        }
        for (User boundUser : boundUsers) {
            List<PriceTemplate> childTemplates = priceTemplateService.list(new LambdaQueryWrapper<PriceTemplate>()
                    .eq(PriceTemplate::getCreatId, boundUser.getId()));
            if (CollectionUtils.isEmpty(childTemplates)) {
                continue;
            }
            for (PriceTemplate childTemplate : childTemplates) {
                if (Objects.equals(childTemplate.getId(), parentTemplateId)) {
                    continue;
                }
                PriceTemplateItem childItem = getItem(childTemplate.getId(), project);
                if (childItem == null) {
                    continue;
                }
                applyTemplateBounds(childItem, project, parentItem.getPrice());
                priceTemplateItemService.updateById(childItem);

                if (syncedKeys.add(syncKey(childTemplate.getId(), project))) {
                    syncChildren(childTemplate.getId(), childItem, project, syncedKeys);
                }
            }
        }
    }

    private PriceTemplateItem getItem(Long templateId, String projectId, Long lineId) {
        return priceTemplateItemService.getOne(new LambdaQueryWrapper<PriceTemplateItem>()
                .eq(PriceTemplateItem::getTemplateId, templateId)
                .eq(PriceTemplateItem::getProjectId, Long.valueOf(projectId))
                .eq(PriceTemplateItem::getLineId, lineId)
                .last("LIMIT 1"));
    }

    private PriceTemplateItem getItem(Long templateId, Project project) {
        return priceTemplateItemService.getOne(new LambdaQueryWrapper<PriceTemplateItem>()
                .eq(PriceTemplateItem::getTemplateId, templateId)
                .and(wrapper -> wrapper.eq(PriceTemplateItem::getProjectTableId, project.getId())
                        .or(w -> w.eq(PriceTemplateItem::getProjectId, Long.valueOf(project.getProjectId()))
                                .eq(PriceTemplateItem::getLineId, Long.valueOf(project.getLineId()))))
                .last("LIMIT 1"));
    }

    private BigDecimal resolveCreatorCost(PriceTemplate template, String projectId, Long lineId) {
        if (template == null || template.getCreatId() == null || template.getCreatId() == 0L) {
            return null;
        }
        User creator = userService.getById(template.getCreatId());
        if (creator == null || creator.getTemplateId() == null) {
            return null;
        }
        PriceTemplateItem parentItem = priceTemplateService.getPriceConfig(creator.getTemplateId(), projectId, lineId.intValue());
        return parentItem == null ? null : parentItem.getPrice();
    }

    private void applyProjectBase(PriceTemplateItem item, Project project) {
        BigDecimal costPrice = zeroIfNull(project.getCostPrice());
        BigDecimal minPrice = project.getPriceMin() == null ? costPrice : project.getPriceMin();
        if (minPrice.compareTo(costPrice) < 0) {
            minPrice = costPrice;
        }
        setCommonProjectFields(item, project);
        item.setCostPrice(costPrice);
        item.setMinPrice(minPrice);
        item.setMaxPrice(project.getPriceMax());
        item.setPrice(clampPrice(item.getPrice(), minPrice, project.getPriceMax()));
    }

    private void applyTemplateBounds(PriceTemplateItem item, Project project, BigDecimal parentPrice) {
        BigDecimal projectMin = project.getPriceMin() == null ? zeroIfNull(project.getCostPrice()) : project.getPriceMin();
        BigDecimal floorPrice = parentPrice == null ? zeroIfNull(item.getCostPrice()) : parentPrice;
        BigDecimal minPrice = max(projectMin, floorPrice);
        BigDecimal maxPrice = project.getPriceMax();
        if (hasMax(maxPrice) && minPrice.compareTo(maxPrice) > 0) {
            throw new BusinessException("价格同步失败：上级成本价高于项目最高价，请先调整项目最高价或上级售价");
        }
        setCommonProjectFields(item, project);
        item.setCostPrice(floorPrice);
        item.setMinPrice(minPrice);
        item.setMaxPrice(maxPrice);
        item.setPrice(clampPrice(item.getPrice(), minPrice, maxPrice));
    }

    private void setCommonProjectFields(PriceTemplateItem item, Project project) {
        item.setProjectTableId(project.getId());
        item.setProjectId(Long.valueOf(project.getProjectId()));
        item.setLineId(Long.valueOf(project.getLineId()));
        item.setProjectName(project.getProjectName());
    }

    private BigDecimal clampPrice(BigDecimal price, BigDecimal minPrice, BigDecimal maxPrice) {
        BigDecimal result = price == null ? minPrice : price;
        if (result.compareTo(minPrice) < 0) {
            result = minPrice;
        }
        if (hasMax(maxPrice) && result.compareTo(maxPrice) > 0) {
            result = maxPrice;
        }
        return result;
    }

    private boolean hasMax(BigDecimal maxPrice) {
        return maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal max(BigDecimal first, BigDecimal second) {
        return first.compareTo(second) >= 0 ? first : second;
    }

    private String syncKey(Long templateId, Project project) {
        return templateId + ":" + project.getProjectId() + ":" + project.getLineId();
    }
}
