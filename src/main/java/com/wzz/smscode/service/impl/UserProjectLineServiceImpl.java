package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wzz.smscode.entity.UserProjectLine;
import com.wzz.smscode.mapper.UserProjectLineMapper;
import com.wzz.smscode.service.UserProjectLineService;
import org.springframework.stereotype.Service;

@Service
public class UserProjectLineServiceImpl extends ServiceImpl<UserProjectLineMapper, UserProjectLine> implements UserProjectLineService {
}
