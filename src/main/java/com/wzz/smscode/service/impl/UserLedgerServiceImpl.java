package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wzz.smscode.entity.UserLedger;
import com.wzz.smscode.mapper.UserLedgerMapper;
import com.wzz.smscode.service.UserLedgerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserLedgerServiceImpl extends ServiceImpl<UserLedgerMapper, UserLedger> implements UserLedgerService {
    @Autowired
    private UserLedgerMapper userLedgerMapper;
}
