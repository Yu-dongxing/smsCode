package com.wzz.smscode.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wzz.smscode.entity.UserProjectLine;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.jmx.export.annotation.ManagedNotification;

@Mapper
public interface UserProjectLineMapper extends BaseMapper<UserProjectLine> {
}
