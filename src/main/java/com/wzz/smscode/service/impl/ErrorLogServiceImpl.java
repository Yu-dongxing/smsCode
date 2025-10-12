package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wzz.smscode.entity.ErrorLog;
import com.wzz.smscode.mapper.ErrorLogMapper;
import com.wzz.smscode.service.ErrorLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ErrorLogServiceImpl extends ServiceImpl<ErrorLogMapper, ErrorLog> implements ErrorLogService {
    @Autowired
    private ErrorLogMapper errorLogMapper;
}
