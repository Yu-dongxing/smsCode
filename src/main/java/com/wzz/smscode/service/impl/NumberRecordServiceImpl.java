package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wzz.smscode.entity.NumberRecord;
import com.wzz.smscode.mapper.NumberRecordMapper;
import com.wzz.smscode.service.NumberRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NumberRecordServiceImpl extends ServiceImpl<NumberRecordMapper, NumberRecord> implements NumberRecordService {
    @Autowired
    private NumberRecordMapper numberRecordMapper;
}
