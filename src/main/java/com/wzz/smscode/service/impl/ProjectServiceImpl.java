package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wzz.smscode.dto.EntityDTO.ProjectDTO;
import com.wzz.smscode.dto.ProjectPriceDetailsDTO;
import com.wzz.smscode.dto.ProjectPriceSummaryDTO;
import com.wzz.smscode.dto.SelectProjectDTO;
import com.wzz.smscode.entity.Project;
import com.wzz.smscode.entity.User;
import com.wzz.smscode.entity.UserProjectLine;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.mapper.ProjectMapper;
import com.wzz.smscode.service.ProjectService;
import com.wzz.smscode.service.UserProjectLineService;
import com.wzz.smscode.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements ProjectService {


    @Autowired
    @Lazy
    private UserService userService;

    // 注入 UserProjectLineService 用于批量保存数据
    @Autowired
    private UserProjectLineService userProjectLineService;

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

    /**
     * 重写 save 方法，在创建新项目后，为所有用户生成对应的项目线路配置
     * @param project 要保存的项目实体
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class) // *** 关键：确保整个方法是事务性的 ***
    public boolean save(Project project) {
        // 1. 首先，保存项目自身，这样项目就会获得一个数据库自增ID
        boolean projectSaved = super.save(project);
        if (!projectSaved) {
            log.error("创建项目基础信息失败: {}", project.getProjectName());
            return false;
        }
        log.info("项目 '{}' 已成功保存，ID为: {}", project.getProjectName(), project.getId());

        // 2. 获取系统中所有的用户
        List<User> allUsers = userService.list();
        if (CollectionUtils.isEmpty(allUsers)) {
            // 如果系统中没有任何用户，那么记录一条日志，直接返回成功，因为项目已经创建了
            log.warn("项目 '{}' 创建成功，但系统中没有找到任何用户，无需分配项目线路。", project.getProjectName());
            return true;
        }

        // 3. 根据您的定价规则，确定要为用户设置的默认售价 (AgentPrice)
        BigDecimal defaultPrice;
        if (project.getPriceMax() != null && project.getPriceMax().compareTo(BigDecimal.ZERO) > 0) {
            defaultPrice = project.getPriceMax();
        } else if (project.getPriceMin() != null && project.getPriceMin().compareTo(BigDecimal.ZERO) > 0) {
            defaultPrice = project.getPriceMin();
        } else {
            defaultPrice = project.getCostPrice();
        }
        // 安全校验，防止所有价格都为空
        if (defaultPrice == null) {
            log.warn("项目 '{}' 的最高价、最低价和成本价均未设置，将为用户分配的默认售价设置为0。", project.getProjectName());
            defaultPrice = BigDecimal.ZERO;
        }

        // 4. 为每一个用户构建 UserProjectLine 实体
        final BigDecimal finalDefaultPrice = defaultPrice; // Lambda表达式中需要final变量
        List<UserProjectLine> linesToInsert = allUsers.stream().map(user -> {
            UserProjectLine line = new UserProjectLine();
            line.setUserId(user.getId());

            // 关联刚刚创建的 Project 的主键 ID
            line.setProjectTableId(project.getId());

            line.setProjectName(project.getProjectName());
            line.setProjectId(project.getProjectId());
            line.setLineId(project.getLineId());

            // 设置成本价
            line.setCostPrice(project.getCostPrice());

            // 设置根据规则计算出的代理售价
            line.setAgentPrice(finalDefaultPrice);

            return line;
        }).collect(Collectors.toList());

        // 5. 批量插入所有用户的项目线路配置
        log.info("准备为 {} 个用户批量插入项目 '{}' 的线路配置...", linesToInsert.size(), project.getProjectName());
        boolean linesSaved = userProjectLineService.saveBatch(linesToInsert);
        if (!linesSaved) {
            // 如果批量插入失败，抛出异常，触发事务回滚，刚才保存的Project也会被删除
            throw new BusinessException("为用户批量创建项目线路配置失败，项目创建操作已回滚。");
        }

        log.info("成功为 {} 个用户创建了项目 '{}' 的线路配置。", linesToInsert.size(), project.getProjectName());
        return true;
    }

    /**
     * [重构] 删除项目时，一并删除所有用户的相关项目线路价格配置
     * @param id 要删除的项目的数据库主键ID
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class) // *** 关键：确保操作的原子性 ***
    public Boolean deleteByID(long id) {
        Project projectToDelete = this.getById(id);
        // 2. 校验项目是否存在
        if (projectToDelete == null) {
            log.warn("尝试删除一个不存在的项目，ID: {}", id);
            throw new BusinessException("删除失败：找不到ID为 " + id + " 的项目。");
        }

        String projectId = projectToDelete.getProjectId();
        String lineId = projectToDelete.getLineId();

        // 3. 构建查询条件，删除所有用户关于此项目的线路配置
        log.info("正在删除项目 '{}' (ID: {})，将同步删除所有用户关联的线路配置 (projectId: {}, lineId: {})...",
                projectToDelete.getProjectName(), id, projectId, lineId);

        LambdaQueryWrapper<UserProjectLine> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(UserProjectLine::getProjectId, projectId);
        deleteWrapper.eq(UserProjectLine::getLineId, lineId);

        boolean linesRemoved = userProjectLineService.remove(deleteWrapper);
        log.info("项目 '{}' 关联的用户线路配置已清理完毕。", projectToDelete.getProjectName());

        // 4. 最后，删除项目本身
        boolean projectRemoved = this.removeById(id);
        if (!projectRemoved) {
            throw new BusinessException("删除项目主体记录失败，操作已回滚。");
        }

        log.info("项目 '{}' (ID: {}) 已被成功删除。", projectToDelete.getProjectName(), id);
        return true;
    }
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