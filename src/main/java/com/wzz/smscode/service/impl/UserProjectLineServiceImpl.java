package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wzz.smscode.entity.UserProjectLine;
import com.wzz.smscode.mapper.UserProjectLineMapper;
import com.wzz.smscode.service.UserProjectLineService;
import org.springframework.stereotype.Service;

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
    
}
