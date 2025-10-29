package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wzz.smscode.dto.project.ProjectPriceInfoDTO;
import com.wzz.smscode.dto.project.SubUserProjectPriceDTO;
import com.wzz.smscode.entity.UserProjectLine;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.mapper.UserProjectLineMapper;
import com.wzz.smscode.service.UserProjectLineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户项目线路表实现类
 */
@Service
public class UserProjectLineServiceImpl extends ServiceImpl<UserProjectLineMapper, UserProjectLine> implements UserProjectLineService {
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
    public List<UserProjectLine> getLinesByUserIds(List<Long> userIds) {
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
            throw new BusinessException(0,"查询为空");
        }

        return projectLine;
    }

    /**
     * [新增] 更新用户的项目线路配置
     * 采用“先删除后新增”的策略，确保操作的原子性
     * @param dto 包含用户ID和新的项目配置列表
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean updateUserProjectLines(SubUserProjectPriceDTO dto) {
        if (dto == null || dto.getUserId() == null) {
            throw new BusinessException("用户信息不能为空");
        }

        Long userId = dto.getUserId();

        // 1. 删除该用户所有旧的配置
        this.remove(new LambdaQueryWrapper<UserProjectLine>().eq(UserProjectLine::getUserId, userId));

        // 2. 如果新的配置列表不为空，则批量插入
        List<ProjectPriceInfoDTO> newProjectPrices = dto.getProjectPrices();
        if (CollectionUtils.isNotEmpty(newProjectPrices)) {
            List<UserProjectLine> newUserLines = newProjectPrices.stream().map(priceDto -> {
                UserProjectLine line = new UserProjectLine();
                line.setUserId(userId);
                // 注意字段映射关系
                line.setProjectTableId(priceDto.getId()); // DTO的id对应实体类的 project_table_id
                line.setProjectName(priceDto.getProjectName());
                line.setProjectId(priceDto.getProjectId());
                line.setLineId(priceDto.getLineId());
                line.setAgentPrice(priceDto.getPrice()); // DTO的price对应实体类的 agent_price
                line.setCostPrice(priceDto.getCostPrice());
                return line;
            }).collect(Collectors.toList());

            // 批量保存
            return this.saveBatch(newUserLines);
        }

        // 如果新列表为空，删除操作执行后即为成功
        return true;
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
