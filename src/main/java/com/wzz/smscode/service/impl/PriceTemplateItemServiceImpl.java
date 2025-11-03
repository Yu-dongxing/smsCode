package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wzz.smscode.entity.PriceTemplateItem;
import com.wzz.smscode.mapper.PriceTemplateItemMapper;
import com.wzz.smscode.service.PriceTemplateItemService;
import org.springframework.stereotype.Service;

@Service
public class PriceTemplateItemServiceImpl extends ServiceImpl<PriceTemplateItemMapper, PriceTemplateItem> implements PriceTemplateItemService {
    // 目前不需要额外方法，继承ServiceImpl即可
}