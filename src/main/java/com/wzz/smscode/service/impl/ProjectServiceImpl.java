package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wzz.smscode.dto.EntityDTO.ProjectDTO;
import com.wzz.smscode.dto.ProjectPriceDetailsDTO;
import com.wzz.smscode.dto.ProjectPriceSummaryDTO;
import com.wzz.smscode.dto.SelectProjectDTO;
import com.wzz.smscode.entity.Project;
import com.wzz.smscode.entity.UserProjectLine;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.mapper.ProjectMapper;
import com.wzz.smscode.service.ProjectService;
import com.wzz.smscode.service.UserProjectLineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements ProjectService {

    @Transactional
    @Override
    public boolean createProject(ProjectDTO projectDTO) {
        // 检查组合键是否已存在
        if (getProject(projectDTO.getProjectId(), projectDTO.getLineId()) != null) {
            throw new BusinessException(
                    String.format("项目线路已存在: projectId=%s, lineId=%s", projectDTO.getProjectId(), projectDTO.getLineId())
            );
        }
        // DTO 转换为 实体
        Project project = new Project();
        BeanUtils.copyProperties(projectDTO, project);

        return this.save(project);
    }

    @Transactional
    @Override
    public boolean updateProject(ProjectDTO projectDTO) {
        Project existingProject = getProject(projectDTO.getProjectId(), projectDTO.getLineId());
        if (existingProject == null) {
            return false; // 记录不存在，更新失败
        }

        Project projectToUpdate = new Project();
        BeanUtils.copyProperties(projectDTO, projectToUpdate);
        // 确保主键 ID 被设置，以便 Mybatis-Plus 按 ID 更新
        projectToUpdate.setId(existingProject.getId());

        return this.updateById(projectToUpdate);
    }

    @Transactional
    @Override
    public boolean updateProject(Project projectDTO) {
        Project existingProject = getProject(projectDTO.getProjectId(), Integer.valueOf(projectDTO.getLineId()));
        if (existingProject == null) {
            return false; // 记录不存在，更新失败
        }

        Project projectToUpdate = new Project();
        BeanUtils.copyProperties(projectDTO, projectToUpdate);
        // 确保主键 ID 被设置，以便 Mybatis-Plus 按 ID 更新
        projectToUpdate.setId(existingProject.getId());

        return this.updateById(projectToUpdate);
    }

    @Transactional
    @Override
    public boolean deleteProject(String projectId, Integer lineId) {
        // TODO: 在删除前，应检查是否有号码记录等关联数据正在使用此线路
        // 例如: if (numberRecordService.isLineInUse(projectId, lineId)) { throw new BusinessException("线路正在使用中，无法删除"); }

        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getProjectId, projectId)
                .eq(Project::getLineId, lineId);

        return this.remove(wrapper);
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
                .select(Project::getLineId); // 优化查询，只选择 lineId 列

        List<Project> projects = this.list(wrapper);

        return projects.stream()
                .map(Project::getLineId)
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
                        p -> p.getProjectId() + "-" + p.getLineId(), // key
                        p -> new ProjectPriceDetailsDTO(p.getPriceMin(),p.getPriceMax(), p.getCostPrice()) // value
                ));
    }

    @Override
    public Map<String, BigDecimal> fillMissingPrices(Map<String, BigDecimal> inputPrices) {
        // 1. 获取所有项目的价格摘要信息（包含最高价）
        Map<String, ProjectPriceSummaryDTO> priceSummaries = getAllProjectPriceSummaries();
        // 2. 获取系统中所有的项目线路
        List<Project> allProjectLines = this.list();

        // 3. 遍历所有线路，检查并补全
        for (Project line : allProjectLines) {
            String priceKey = line.getProjectId() + "-" + line.getLineId();
            // 使用 computeIfAbsent 更简洁
            inputPrices.computeIfAbsent(priceKey, k -> {
                ProjectPriceSummaryDTO summary = priceSummaries.get(line.getProjectId());
                if (summary != null && summary.getMaxPrice() != null) {
                    log.info("价格补全: 线路 '{}' 缺失, 使用项目 '{}' 的最高价 '{}' 进行填充。", k, line.getProjectId(), summary.getMaxPrice());
                    return summary.getMaxPrice();
                } else {
                    log.warn("价格补全警告: 线路 '{}' 所属的项目 '{}' 没有找到价格摘要信息，使用线路自身成本价 {} 填充。", k, line.getProjectId(), line.getCostPrice());
                    return line.getCostPrice(); // 降级方案
                }
            });
        }
        return inputPrices;
    }

    @Override
    public Map<String, ProjectPriceSummaryDTO> getAllProjectPriceSummaries() {
        // 此方法为内部服务，依赖于 Mapper 中的自定义 SQL
        List<ProjectPriceSummaryDTO> summaries = this.baseMapper.selectProjectPriceSummaries();
        return summaries.stream()
                .collect(Collectors.toMap(ProjectPriceSummaryDTO::getProjectId, Function.identity(), (a, b) -> a));
    }

    @Override
    public Boolean deleteByID(long id) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getId, id);
        return this.remove(wrapper);
    }

    @Autowired
    private UserProjectLineService userProjectLineService;
    /**
     * 新增：根据用户ID查询其有权访问的项目列表
     * @param userId 用户ID
     * @return 项目DTO列表
     */
    @Override
    public List<UserProjectLine> listUserProjects(Long userId) {
        // 1. 获取该用户所有的项目线路配置记录

//        if (userProjectLines == null || userProjectLines.isEmpty()) {
//            return Collections.emptyList(); // 如果用户没有任何项目，返回空列表
//        }
//
//        // 2. 使用Java Stream API进行处理
//        return userProjectLines.stream()
//                // 将 UserProjectLine 对象映射成 ProjectDTO 对象
//                .map(line -> new SelectProjectDTO(line.getProjectId(), line.getProjectName()))
//                // 去除重复的项目（根据ProjectDTO的equals和hashCode方法）
//                .distinct()
//                // 收集结果为List
//                .collect(Collectors.toList());
        return userProjectLineService.getLinesByUserId(userId);
    }
}