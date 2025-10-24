package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wzz.smscode.entity.UserProjectLine;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.mapper.UserProjectLineMapper;
import com.wzz.smscode.service.UserProjectLineService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

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

}
