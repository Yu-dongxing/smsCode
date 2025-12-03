package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wzz.smscode.dto.project.ProjectPriceDetailsDTO;
import com.wzz.smscode.dto.project.ProjectPriceSummaryDTO;
import com.wzz.smscode.entity.*;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.mapper.ProjectMapper;
import com.wzz.smscode.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements ProjectService {

    @Autowired
    @Lazy
    private UserService userService;

    @Autowired
    private UserProjectLineService userProjectLineService;

    // 【新增注入】用于操作模板和模板项
    @Autowired
    @Lazy
    private PriceTemplateService priceTemplateService;

    @Autowired
    @Lazy
    private PriceTemplateItemService priceTemplateItemService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean updateProject(Project projectDTO) {
        // 1. 参数校验
        if (projectDTO.getId() == null) {
            throw new BusinessException("更新项目失败：必须提供项目的主键ID。");
        }

        // 2. 查询项目更新前的状态
        Project existingProject = this.getById(projectDTO.getId());
        if (existingProject == null) {
            log.warn("尝试更新一个不存在的项目，ID: {}", projectDTO.getId());
            return false;
        }

        // 3. 更新 Project 表自身
        Project projectToUpdate = new Project();
        BeanUtils.copyProperties(projectDTO, projectToUpdate);
        boolean projectUpdated = this.updateById(projectToUpdate);
        if (!projectUpdated) {
            log.error("更新项目基础信息失败, Project ID: {}", projectDTO.getId());
            throw new BusinessException("更新项目基础信息失败，操作已回滚。");
        }
        log.info("项目基础信息已更新, ID: {}", projectDTO.getId());
        log.info("开始同步更新关联的价格模板项配置...");
        LambdaUpdateWrapper<PriceTemplateItem> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(PriceTemplateItem::getProjectTableId, existingProject.getId()); // 根据项目主表ID匹配
        try {
            updateWrapper.set(PriceTemplateItem::getProjectId, Long.valueOf(projectDTO.getProjectId()));
            updateWrapper.set(PriceTemplateItem::getLineId, Long.valueOf(projectDTO.getLineId()));
        } catch (NumberFormatException e) {
            log.error("项目ID或线路ID格式转换错误，无法同步模板", e);
        }
        updateWrapper.set(PriceTemplateItem::getProjectName, projectDTO.getProjectName());
        updateWrapper.set(PriceTemplateItem::getCostPrice, projectDTO.getCostPrice());
        updateWrapper.set(PriceTemplateItem::getMinPrice, projectDTO.getPriceMin());
        updateWrapper.set(PriceTemplateItem::getMaxPrice, projectDTO.getPriceMax());
        boolean itemsUpdated = priceTemplateItemService.update(updateWrapper);
        log.info("关联的价格模板项同步完成: {}, Project ID: {}", itemsUpdated, projectDTO.getId());
        return true;
    }

    @Override
    public Project getProject(String projectId, Integer lineId) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getProjectId, projectId)
                .eq(Project::getLineId, lineId);
        return this.getOne(wrapper);
    }

    @Override
    public List<String> listLines(String projectId) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getProjectId, projectId)
                .select(Project::getLineId);
        List<Project> projects = this.list(wrapper);
        return projects.stream()
                .map(Project::getLineId)
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> listLinesWithCamelCaseKey(String projectId) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getProjectId, projectId)
                .select(Project::getLineId, Project::getLineName);
        List<Project> projects = this.list(wrapper);
        return projects.stream()
                .map(project -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("lineId", project.getLineId());
                    map.put("lineName", project.getLineName());
                    return map;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> listLinesWithCamelCaseKeyFor(Long userId, String projectId) {
        if (userId == null || projectId == null || projectId.isEmpty()) {
            throw new BusinessException("用户ID和项目ID不能为空");
        }
        // 这里依然保留查询 UserProjectLine 的逻辑，因为这属于“查询用户权限”，
        // 但具体的 UserProjectLine 数据维护可能由其他逻辑（如分配模板）来触发，而不是由 Project 的 CRUD 触发。
        LambdaQueryWrapper<UserProjectLine> userLineQuery = new LambdaQueryWrapper<>();
        userLineQuery.eq(UserProjectLine::getUserId, userId)
                .eq(UserProjectLine::getProjectId, projectId)
                .eq(UserProjectLine::isStatus, true)
                .select(UserProjectLine::getLineId, UserProjectLine::getProjectTableId);

        List<UserProjectLine> userLines = userProjectLineService.list(userLineQuery);

        if (CollectionUtils.isEmpty(userLines)) {
            return Collections.emptyList();
        }

        List<Long> projectTableIds = userLines.stream()
                .map(UserProjectLine::getProjectTableId)
                .distinct()
                .collect(Collectors.toList());

        LambdaQueryWrapper<Project> projectQuery = new LambdaQueryWrapper<>();
        projectQuery.in(Project::getId, projectTableIds)
                .select(Project::getId, Project::getLineName, Project::getLineId);

        Map<Long, Project> projectInfoMap = this.list(projectQuery).stream()
                .collect(Collectors.toMap(Project::getId, Function.identity()));

        return userLines.stream()
                .map(userLine -> {
                    Project projectInfo = projectInfoMap.get(userLine.getProjectTableId());
                    Map<String, Object> map = new HashMap<>();
                    map.put("lineId", userLine.getLineId());
                    map.put("lineName", (projectInfo != null) ? projectInfo.getLineName() : "未知线路");
                    return map;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, ProjectPriceDetailsDTO> getAllProjectPrices() {
        List<Project> allLines = this.list();
        if (allLines.isEmpty()) {
            return Collections.emptyMap();
        }
        return allLines.stream()
                .collect(Collectors.toMap(
                        p -> p.getProjectId() + "-" + p.getLineId(),
                        p -> new ProjectPriceDetailsDTO(p.getPriceMin(),p.getPriceMax(), p.getCostPrice())
                ));
    }

    @Override
    public Map<String, BigDecimal> fillMissingPrices(Map<String, BigDecimal> inputPrices) {
        Map<String, ProjectPriceSummaryDTO> priceSummaries = getAllProjectPriceSummaries();
        List<Project> allProjectLines = this.list();

        for (Project line : allProjectLines) {
            String priceKey = line.getProjectId() + "-" + line.getLineId();
            inputPrices.computeIfAbsent(priceKey, k -> {
                ProjectPriceSummaryDTO summary = priceSummaries.get(line.getProjectId());
                if (summary != null && summary.getMaxPrice() != null) {
                    return summary.getMaxPrice();
                } else {
                    return line.getCostPrice();
                }
            });
        }
        return inputPrices;
    }

    @Override
    public Map<String, ProjectPriceSummaryDTO> getAllProjectPriceSummaries() {
        List<ProjectPriceSummaryDTO> summaries = this.baseMapper.selectProjectPriceSummaries();
        return summaries.stream()
                .collect(Collectors.toMap(ProjectPriceSummaryDTO::getProjectId, Function.identity(), (a, b) -> a));
    }

    /**
     * 重写 save 方法
     * 【修改】: 在创建新项目后，不再为用户直接生成线路，而是同步更新到所有已有的“价格模板”中。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(Project project) {
        if(project.getLineId() == null || project.getProjectId() == null || project.getProjectName() == null) {
            throw new BusinessException(0,"项目id，项目名称，线路id不能为空");
        }
        // 1. 保存项目自身
        boolean projectSaved = super.save(project);
        if (!projectSaved) {
            log.error("创建项目基础信息失败: {}", project.getProjectName());
            return false;
        }
        log.info("项目 '{}' 已成功保存，ID为: {}", project.getProjectName(), project.getId());

        // 2. 【修改】获取系统中所有的价格模板 (PriceTemplate)
        List<PriceTemplate> allTemplates = priceTemplateService.list();
        if (CollectionUtils.isEmpty(allTemplates)) {
            log.warn("项目 '{}' 创建成功，但系统中没有找到任何价格模板，无需同步。", project.getProjectName());
            return true;
        }

        // 3. 准备批量插入的模板项
        List<PriceTemplateItem> itemsToInsert = new ArrayList<>();

        // 确定初始价格逻辑：优先使用最低限价，其次使用成本价
        BigDecimal initialPrice = project.getPriceMin() != null && project.getPriceMin().compareTo(BigDecimal.ZERO) > 0
                ? project.getPriceMin()
                : project.getCostPrice();

        for (PriceTemplate template : allTemplates) {
            PriceTemplateItem item = new PriceTemplateItem();
            item.setTemplateId(template.getId());
            item.setProjectTableId(project.getId()); // 关联项目主键ID

            try {
                item.setProjectId(Long.valueOf(project.getProjectId()));
                item.setLineId(Long.valueOf(project.getLineId()));
            } catch (NumberFormatException e) {
                log.error("项目ID或线路ID非数字，跳过该项模板同步: {}", project.getProjectName());
                continue;
            }

            item.setProjectName(project.getProjectName());

            // 设置价格相关
            item.setCostPrice(project.getCostPrice());
            item.setPrice(initialPrice); // 模板项的默认售价
            item.setMinPrice(project.getPriceMin());
            item.setMaxPrice(project.getPriceMax());

            itemsToInsert.add(item);
        }

        // 4. 批量保存模板项
        if (!itemsToInsert.isEmpty()) {
            log.info("准备为 {} 个模板批量插入项目 '{}' 的配置项...", itemsToInsert.size(), project.getProjectName());
            boolean itemsSaved = priceTemplateItemService.saveBatch(itemsToInsert);
            if (!itemsSaved) {
                throw new BusinessException("为模板批量创建项目配置失败，操作已回滚。");
            }
        }

        return true;
    }

    /**
     * 重写 deleteByID 方法
     * 【修改】: 删除项目时，同步删除关联的“价格模板项”，不再删除用户线路配置(UserProjectLine)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByID(long id) {
        Project projectToDelete = this.getById(id);
        if (projectToDelete == null) {
            log.warn("尝试删除一个不存在的项目，ID: {}", id);
            throw new BusinessException("删除失败：找不到ID为 " + id + " 的项目。");
        }

        // 1. 【修改】删除所有模板中关联该项目的配置项
        log.info("正在删除项目 '{}' (ID: {})，将同步删除所有关联的价格模板项...", projectToDelete.getProjectName(), id);

        LambdaQueryWrapper<PriceTemplateItem> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(PriceTemplateItem::getProjectTableId, id);

        // 执行删除模板项
        priceTemplateItemService.remove(deleteWrapper);
        log.info("项目 '{}' 关联的模板项配置已清理完毕。", projectToDelete.getProjectName());

        // 2. 删除项目本身
        boolean projectRemoved = this.removeById(id);
        if (!projectRemoved) {
            throw new BusinessException("删除项目主体记录失败，操作已回滚。");
        }

        log.info("项目 '{}' (ID: {}) 已被成功删除。", projectToDelete.getProjectName(), id);
        return true;
    }

    @Override
    public List<UserProjectLine> listUserProjects(Long userId) {
        return userProjectLineService.getLinesByUserId(userId);
    }
}