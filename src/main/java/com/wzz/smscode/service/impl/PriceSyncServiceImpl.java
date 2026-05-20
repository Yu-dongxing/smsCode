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
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

        Long numericProjectId = parseLong(project.getProjectId(), "Project ID must be numeric");
        Long lineId = parseLong(project.getLineId(), "Line ID must be numeric");
        List<PriceTemplateItem> projectItems = priceTemplateItemService.list(new LambdaQueryWrapper<PriceTemplateItem>()
                .and(wrapper -> wrapper.eq(PriceTemplateItem::getProjectTableId, project.getId())
                        .or(w -> w.eq(PriceTemplateItem::getProjectId, numericProjectId)
                                .eq(PriceTemplateItem::getLineId, lineId))));
        if (CollectionUtils.isEmpty(projectItems)) {
            log.info("Project price sync skipped: no template items, projectId={}, lineId={}",
                    project.getProjectId(), project.getLineId());
            return;
        }

        Map<Long, PriceTemplateItem> itemByTemplateId = projectItems.stream()
                .filter(item -> item.getTemplateId() != null)
                .collect(Collectors.toMap(
                        PriceTemplateItem::getTemplateId,
                        Function.identity(),
                        (first, second) -> first
                ));

        List<PriceTemplate> templates = priceTemplateService.list();
        if (CollectionUtils.isEmpty(templates)) {
            return;
        }
        Map<Long, PriceTemplate> templateById = templates.stream()
                .filter(template -> template.getId() != null)
                .collect(Collectors.toMap(PriceTemplate::getId, Function.identity(), (first, second) -> first));
        Map<Long, List<PriceTemplate>> templatesByCreator = templates.stream()
                .filter(template -> template.getCreatId() != null)
                .collect(Collectors.groupingBy(PriceTemplate::getCreatId));

        Set<Long> templateIds = templates.stream()
                .map(PriceTemplate::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, List<User>> usersByTemplateId = loadUsersByTemplateId(templateIds);
        Map<Long, User> usersById = usersByTemplateId.values().stream()
                .flatMap(List::stream)
                .filter(user -> user.getId() != null)
                .collect(Collectors.toMap(User::getId, Function.identity(), (first, second) -> first));

        Set<Long> syncedTemplateIds = new HashSet<>();
        List<PriceTemplateItem> itemsToUpdate = new ArrayList<>();
        Queue<TemplateSyncNode> queue = new ArrayDeque<>();

        for (PriceTemplate rootTemplate : templatesByCreator.getOrDefault(0L, Collections.emptyList())) {
            PriceTemplateItem rootItem = itemByTemplateId.get(rootTemplate.getId());
            if (rootItem == null) {
                continue;
            }
            applyProjectBase(rootItem, project);
            markSynced(rootTemplate.getId(), rootItem, syncedTemplateIds, itemsToUpdate, queue);
        }

        while (!queue.isEmpty()) {
            TemplateSyncNode parent = queue.poll();
            List<User> boundUsers = usersByTemplateId.getOrDefault(parent.templateId(), Collections.emptyList());
            for (User boundUser : boundUsers) {
                List<PriceTemplate> childTemplates = templatesByCreator.getOrDefault(boundUser.getId(), Collections.emptyList());
                for (PriceTemplate childTemplate : childTemplates) {
                    Long childTemplateId = childTemplate.getId();
                    if (Objects.equals(childTemplateId, parent.templateId()) || syncedTemplateIds.contains(childTemplateId)) {
                        continue;
                    }
                    PriceTemplateItem childItem = itemByTemplateId.get(childTemplateId);
                    if (childItem == null) {
                        continue;
                    }
                    applyTemplateBounds(childItem, project, parent.item().getPrice());
                    markSynced(childTemplateId, childItem, syncedTemplateIds, itemsToUpdate, queue);
                }
            }
        }

        for (PriceTemplateItem item : projectItems) {
            Long templateId = item.getTemplateId();
            if (templateId == null || syncedTemplateIds.contains(templateId)) {
                continue;
            }
            PriceTemplate template = templateById.get(templateId);
            BigDecimal floorPrice = resolveCreatorCost(template, usersById, itemByTemplateId);
            applyTemplateBounds(item, project, floorPrice);
            itemsToUpdate.add(item);
            syncedTemplateIds.add(templateId);
        }

        if (!itemsToUpdate.isEmpty()) {
            priceTemplateItemService.updateBatchById(itemsToUpdate, 500);
        }
        log.info("项目价格同步已完成，projectId={}, lineId={}, updatedItems={}",
                project.getProjectId(), project.getLineId(), itemsToUpdate.size());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void syncByTemplateChanged(Long templateId) {
        if (templateId == null) {
            return;
        }
        List<PriceTemplateItem> templateItems = priceTemplateItemService.list(new LambdaQueryWrapper<PriceTemplateItem>()
                .eq(PriceTemplateItem::getTemplateId, templateId));
        if (CollectionUtils.isEmpty(templateItems)) {
            return;
        }

        List<Long> projectTableIds = templateItems.stream()
                .map(PriceTemplateItem::getProjectTableId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Project> projectsById = CollectionUtils.isEmpty(projectTableIds)
                ? Collections.emptyMap()
                : projectService.listByIds(projectTableIds).stream()
                .collect(Collectors.toMap(Project::getId, Function.identity(), (first, second) -> first));

        Set<String> syncedProjects = new HashSet<>();
        for (PriceTemplateItem templateItem : templateItems) {
            Project project = projectsById.get(templateItem.getProjectTableId());
            if (project == null) {
                project = projectService.getProject(String.valueOf(templateItem.getProjectId()), templateItem.getLineId().intValue());
            }
            if (project == null) {
                log.warn("Price sync skipped: template item {} has no project config", templateItem.getId());
                continue;
            }
            String projectKey = project.getProjectId() + ":" + project.getLineId();
            if (syncedProjects.add(projectKey)) {
                syncByProjectChanged(project);
            }
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
            throw new BusinessException("Price config is lower than parent cost, please adjust parent template");
        }
    }

    private Map<Long, List<User>> loadUsersByTemplateId(Set<Long> templateIds) {
        if (CollectionUtils.isEmpty(templateIds)) {
            return Collections.emptyMap();
        }
        List<User> users = userService.list(new LambdaQueryWrapper<User>()
                .in(User::getTemplateId, templateIds)
                .select(User::getId, User::getTemplateId, User::getIsAgent));
        if (CollectionUtils.isEmpty(users)) {
            return Collections.emptyMap();
        }
        return users.stream()
                .filter(user -> user.getTemplateId() != null)
                .collect(Collectors.groupingBy(User::getTemplateId));
    }

    private void markSynced(Long templateId,
                            PriceTemplateItem item,
                            Set<Long> syncedTemplateIds,
                            List<PriceTemplateItem> itemsToUpdate,
                            Queue<TemplateSyncNode> queue) {
        if (templateId == null || !syncedTemplateIds.add(templateId)) {
            return;
        }
        itemsToUpdate.add(item);
        queue.offer(new TemplateSyncNode(templateId, item));
    }

    private BigDecimal resolveCreatorCost(PriceTemplate template,
                                          Map<Long, User> usersById,
                                          Map<Long, PriceTemplateItem> itemByTemplateId) {
        if (template == null || template.getCreatId() == null || template.getCreatId() == 0L) {
            return null;
        }
        User creator = usersById.get(template.getCreatId());
        if (creator == null || creator.getTemplateId() == null) {
            return null;
        }
        PriceTemplateItem parentItem = itemByTemplateId.get(creator.getTemplateId());
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

    private Long parseLong(String value, String message) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(message);
        }
    }

    @AllArgsConstructor
    private static class TemplateSyncNode {
        private Long templateId;
        private PriceTemplateItem item;

        Long templateId() {
            return templateId;
        }

        PriceTemplateItem item() {
            return item;
        }
    }
}
