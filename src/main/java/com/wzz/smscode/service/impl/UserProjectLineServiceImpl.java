package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wzz.smscode.dto.project.ProjectPriceInfoDTO;
import com.wzz.smscode.dto.project.SubUserProjectPriceDTO;
import com.wzz.smscode.entity.Project;
import com.wzz.smscode.entity.UserProjectLine;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.mapper.UserProjectLineMapper;
import com.wzz.smscode.service.ProjectService;
import com.wzz.smscode.service.UserProjectLineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户项目线路表实现类
 */
@Service
public class UserProjectLineServiceImpl extends ServiceImpl<UserProjectLineMapper, UserProjectLine> implements UserProjectLineService {


    @Autowired
    @Lazy
    private ProjectService projectService;

    /**
     * 根据用户ID获取其所有项目线路配置
     * @param userId 用户ID
     * @return 项目线路配置列表
     */
    @Override
    public List<UserProjectLine> getLinesByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        return this.list(new LambdaQueryWrapper<UserProjectLine>()
                .eq(UserProjectLine::getUserId, userId));
    }

//    @Override
//    public IPage<UserProjectLine> pageLinesByUserId(Long agentId, Page<AgentProjectPriceDTO> page) {
//        return null;
//    }
//
//    @Override
//    public IPage<UserProjectLine> pageLinesByUserId(Long userId, IPage<UserProjectLine> page) {
//        // 使用 MyBatis-Plus 的 Wrapper 构建查询条件
//        LambdaQueryWrapper<UserProjectLine> wrapper = new LambdaQueryWrapper<>();
//        wrapper.eq(UserProjectLine::getUserId, userId); // 假设关联用户的字段是 userId
//        // baseMapper 就是继承 ServiceImpl 后自带的 Mapper 对象
//        return baseMapper.selectPage(page, wrapper);
//    }

    @Override
    public List<UserProjectLine> getLinesByUserIds(List<Long> userIds,String userName) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyList();
        }
        return this.list(new LambdaQueryWrapper<UserProjectLine>().in(UserProjectLine::getUserId, userIds));
    }

    @Override
    public UserProjectLine getByProjectIdLineID(String projectId, Integer lineId,Long userId) {
        LambdaQueryWrapper<UserProjectLine> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserProjectLine::getProjectId,projectId);
        queryWrapper.eq(UserProjectLine::getLineId, lineId);
        queryWrapper.eq(UserProjectLine::getUserId, userId);
        UserProjectLine projectLine = this.getOne(queryWrapper);
        if (projectLine == null){
            throw new BusinessException(0,"当前用户没有该项目的价格配置");
        }

        return projectLine;
    }

    /**
     * [重构后] 更新用户的项目线路配置，并加入价格校验逻辑
     * @param dto 包含用户ID和新的项目配置列表
     * @param editorId 执行此操作的管理员或代理的ID (约定：0为管理员)
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean updateUserProjectLines(SubUserProjectPriceDTO dto, Long editorId) {
        if (dto == null || dto.getUserId() == null || editorId == null) {
            throw new BusinessException("用户或操作者信息不能为空");
        }

        Long targetUserId = dto.getUserId();
        List<ProjectPriceInfoDTO> incomingPrices = dto.getProjectPrices();
        boolean isAdmin = editorId == 0L;

        // 如果传入的配置列表为空，则直接删除该用户的所有配置
        if (CollectionUtils.isEmpty(incomingPrices)) {
            this.remove(new LambdaQueryWrapper<UserProjectLine>().eq(UserProjectLine::getUserId, targetUserId));
            return true;
        }

        // [正确方式] 1. 根据传入的所有 projectId 和 lineId 组合，一次性查询出所有相关的总项目信息
        LambdaQueryWrapper<Project> projectQueryWrapper = new LambdaQueryWrapper<>();
        projectQueryWrapper.and(wrapper -> {
            for (int i = 0; i < incomingPrices.size(); i++) {
                ProjectPriceInfoDTO priceDto = incomingPrices.get(i);
                wrapper.or(w -> w.eq(Project::getProjectId, priceDto.getProjectId())
                        .eq(Project::getLineId, priceDto.getLineId()));
            }
        });
        List<Project> relevantProjects = projectService.list(projectQueryWrapper);

        // 将查询结果转换为Map，方便快速查找。Key: "projectId:lineId"
        Map<String, Project> projectDetailsMap = relevantProjects.stream()
                .collect(Collectors.toMap(
                        p -> p.getProjectId() + ":" + p.getLineId(),
                        Function.identity()
                ));

        // 2. 如果是代理操作，获取代理自己的价格配置
        Map<String, BigDecimal> editorPriceMap = Collections.emptyMap();
        if (!isAdmin) {
            List<UserProjectLine> editorLines = this.list(new LambdaQueryWrapper<UserProjectLine>().eq(UserProjectLine::getUserId, editorId));
            if (CollectionUtils.isNotEmpty(editorLines)) {
                editorPriceMap = editorLines.stream()
                        .collect(Collectors.toMap(
                                line -> line.getProjectId() + ":" + line.getLineId(),
                                UserProjectLine::getAgentPrice
                        ));
            }
        }

        // 3. 获取目标用户已有的配置
        List<UserProjectLine> existingLines = this.list(new LambdaQueryWrapper<UserProjectLine>()
                .eq(UserProjectLine::getUserId, targetUserId));
        Map<String, UserProjectLine> existingLinesMap = existingLines.stream()
                .collect(Collectors.toMap(
                        line -> line.getProjectId() + ":" + line.getLineId(),
                        Function.identity(),
                        (first, second) -> first // 防御重复数据
                ));

        List<UserProjectLine> linesToInsert = new ArrayList<>();
        List<UserProjectLine> linesToUpdate = new ArrayList<>();

        // 4. 遍历传入的配置，进行校验和分类
        for (ProjectPriceInfoDTO priceDto : incomingPrices) {
            String key = priceDto.getProjectId() + ":" + priceDto.getLineId();
            BigDecimal newPrice = priceDto.getPrice();

            // 【新增】获取传入的状态
            Boolean newStatus = priceDto.getStatus();

            // 从Map中获取权威的项目信息
            Project project = projectDetailsMap.get(key);
            if (project == null) {
                // 如果在总项目表中都找不到对应的项目线路，可以选择跳过或抛出异常
                // 这里选择记录日志并跳过，更为健壮
//                log.info("更新用户项目失败：找不到对应的总项目。ProjectId: {}, LineId: {}", priceDto.getProjectId(), priceDto.getLineId());
                continue;
            }

            // 执行价格校验
            validatePrice(priceDto, newPrice, isAdmin, editorPriceMap, project);

            UserProjectLine existingLine = existingLinesMap.get(key);
            if (existingLine != null) {
                // --- 更新逻辑 ---
                boolean needsUpdate = false;

                // 检查价格是否有变化
                if (newPrice != null && (existingLine.getAgentPrice() == null || newPrice.compareTo(existingLine.getAgentPrice()) != 0)) {
                    existingLine.setAgentPrice(newPrice);
                    needsUpdate = true;
                }

                // 【新增】检查状态是否有变化
                if (newStatus != null && !newStatus.equals(existingLine.isStatus())) {
                    existingLine.setStatus(newStatus);
                    needsUpdate = true;
                }

                if (needsUpdate) {
                    linesToUpdate.add(existingLine);
                }
                // 处理过的从map中移除，剩下的就是要被删除的
                existingLinesMap.remove(key);
            } else {
                // --- 新增逻辑 ---
                UserProjectLine newLine = new UserProjectLine();
                newLine.setUserId(targetUserId);
                newLine.setProjectTableId(project.getId());
                newLine.setProjectName(project.getProjectName());
                newLine.setProjectId(project.getProjectId());
                newLine.setLineId(project.getLineId());
                newLine.setAgentPrice(newPrice);
                newLine.setCostPrice(project.getCostPrice());

                // 【新增】设置状态，如果传入为null，则默认为 true (启用)
                newLine.setStatus(newStatus != null ? newStatus : true);

                linesToInsert.add(newLine);
            }
        }

        // 5. 删除多余的配置（在传入的列表中不存在，但在数据库中存在的）
        if (!existingLinesMap.isEmpty()) {
            List<Long> idsToDelete = existingLinesMap.values().stream()
                    .map(UserProjectLine::getId)
                    .collect(Collectors.toList());
            this.removeByIds(idsToDelete);
        }

        // 6. 批量执行数据库操作
        if (CollectionUtils.isNotEmpty(linesToInsert)) {
            this.saveBatch(linesToInsert);
        }
        if (CollectionUtils.isNotEmpty(linesToUpdate)) {
            this.updateBatchById(linesToUpdate);
        }

        return true;
    }

    /**
     * [正确方式] 价格校验的私有辅助方法
     * @param priceDto          当前项目价格信息(仅用于获取projectId和lineId)
     * @param newPrice          要设置的新价格
     * @param isAdmin           操作者是否是管理员
     * @param editorPriceMap    如果是代理，此为代理自己的价格配置
     * @param project           从数据库查到的权威的总项目信息
     */
    private void validatePrice(ProjectPriceInfoDTO priceDto, BigDecimal newPrice, boolean isAdmin, Map<String, BigDecimal> editorPriceMap, Project project) {
        if (newPrice == null) return;

        if (project == null) {
            throw new BusinessException(String.format("数据错误：系统中不存在项目ID为 '%s' 且线路ID为 '%s' 的项目。",
                    priceDto.getProjectId(), priceDto.getLineId()));
        }

        String projectName = project.getProjectName();
        BigDecimal maxPrice = project.getPriceMax();
        BigDecimal minPrice = project.getPriceMin();

        // 规则1: 任何角色的编辑，价格都不能超过项目最高价
        if (maxPrice != null && newPrice.compareTo(maxPrice) > 0) {
            throw new BusinessException(String.format("项目'%s'的价格[%s]不能超过最高价[%s]", projectName, newPrice, maxPrice));
        }

        if (isAdmin) {
            // 规则2: 管理员编辑，价格不能低于项目最低价
            if (minPrice != null && newPrice.compareTo(minPrice) < 0) {
                throw new BusinessException(String.format("管理员设置项目'%s'的价格[%s]不能低于最低价[%s]", projectName, newPrice, minPrice));
            }
        } else {
            // 规则3: 代理编辑，价格不能低于代理自己的价格
            String key = project.getProjectId() + ":" + project.getLineId();
            BigDecimal editorOwnPrice = editorPriceMap.get(key);

            if (editorOwnPrice == null) {
                throw new BusinessException(String.format("代理您没有项目'%s'的配置，无法为下级设置价格", projectName));
            }
            if (newPrice.compareTo(editorOwnPrice) < 0) {
                throw new BusinessException(String.format("代理为项目'%s'设置的价格[%s]不能低于您自己的价格[%s]", projectName, newPrice, editorOwnPrice));
            }
        }
    }

    @Override
    public Boolean updateUserProjectLinesById(UserProjectLine userProjectLine) {
        if (userProjectLine == null || userProjectLine.getId() == null) {
            throw new BusinessException(0, "传入的更新参数无效，ID不能为空");
        }
        int updatedRows = this.baseMapper.updateById(userProjectLine);
        return updatedRows > 0;
    }

}
