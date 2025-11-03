package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wzz.smscode.dto.PriceTemplateCreateDTO;
import com.wzz.smscode.dto.PriceTemplateItemDTO;
import com.wzz.smscode.dto.PriceTemplateResponseDTO;
import com.wzz.smscode.entity.PriceTemplate;
import com.wzz.smscode.entity.PriceTemplateItem;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.mapper.PriceTemplateMapper;
import com.wzz.smscode.service.PriceTemplateItemService;
import com.wzz.smscode.service.PriceTemplateService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PriceTemplateServiceImpl extends ServiceImpl<PriceTemplateMapper, PriceTemplate> implements PriceTemplateService {

    @Autowired
    private PriceTemplateItemService priceTemplateItemService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean createTemplate(PriceTemplateCreateDTO createDTO, Long creatorId) { // 修改点 1: 增加 creatorId 参数
        if (createDTO == null || createDTO.getName() == null || createDTO.getName().trim().isEmpty()) {
            throw new BusinessException("模板名称不能为空");
        }

        // 1. 创建模板主体
        PriceTemplate template = new PriceTemplate();
        template.setName(createDTO.getName());
        template.setCreatId(creatorId); // 修改点 2: 设置创建者ID
        this.save(template); // 保存后，template对象会获得ID

        // 2. 批量创建模板配置项
        if (!CollectionUtils.isEmpty(createDTO.getItems())) {
            List<PriceTemplateItem> items = createDTO.getItems().stream().map(itemDTO -> {
                PriceTemplateItem item = new PriceTemplateItem();
                BeanUtils.copyProperties(itemDTO, item);
                item.setTemplateId(template.getId()); // 关联模板ID
                return item;
            }).collect(Collectors.toList());
            priceTemplateItemService.saveBatch(items);
        }
        return true;
    }

    @Override
    public List<PriceTemplateResponseDTO> listTemplatesByCreator(Long creatorId) {
        // 1. 构建查询条件
        LambdaQueryWrapper<PriceTemplate> queryWrapper = new LambdaQueryWrapper<>();

        // 修改点：检查 creatorId 是否为-1。如果不是-1，则添加查询条件。
        // 使用-1L确保是Long类型比较，同时进行null检查更严谨。
        if (creatorId != null && !creatorId.equals(-1L)) {
            queryWrapper.eq(PriceTemplate::getCreatId, creatorId);
        }
        // 如果 creatorId 为 null 或 -1L，则不添加 creatId 的查询条件，从而查询所有模板

        // 2. 根据条件查询模板列表
        List<PriceTemplate> templates = this.list(queryWrapper);

        if (CollectionUtils.isEmpty(templates)) {
            return Collections.emptyList();
        }

        // 3. 后续逻辑与原来一致，无需修改
        List<Long> templateIds = templates.stream().map(PriceTemplate::getId).collect(Collectors.toList());
        List<PriceTemplateItem> allItems = priceTemplateItemService.list(
                new LambdaQueryWrapper<PriceTemplateItem>().in(PriceTemplateItem::getTemplateId, templateIds)
        );
        Map<Long, List<PriceTemplateItem>> itemsByTemplateId = allItems.stream()
                .collect(Collectors.groupingBy(PriceTemplateItem::getTemplateId));

        return templates.stream().map(template -> {
            PriceTemplateResponseDTO responseDTO = new PriceTemplateResponseDTO();
            responseDTO.setId(template.getId());
            responseDTO.setName(template.getName());
            // creatId 也可以选择性返回
            responseDTO.setCreatId(template.getCreatId());

            List<PriceTemplateItem> items = itemsByTemplateId.getOrDefault(template.getId(), Collections.emptyList());
            List<PriceTemplateItemDTO> itemDTOs = items.stream().map(item -> {
                PriceTemplateItemDTO itemDTO = new PriceTemplateItemDTO();
                BeanUtils.copyProperties(item, itemDTO);
                return itemDTO;
            }).collect(Collectors.toList());

            responseDTO.setItems(itemDTOs);
            return responseDTO;
        }).collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean updateTemplate(Long templateId, PriceTemplateCreateDTO updateDTO, Long operatorId) { // 修改点 5: 增加 operatorId
        if (templateId == null || updateDTO == null || updateDTO.getName() == null) {
            throw new BusinessException("参数无效");
        }

        // 修改点 6: 增加权限校验
        PriceTemplate existingTemplate = this.getById(templateId);
        if (existingTemplate == null || !existingTemplate.getCreatId().equals(operatorId)) {
            throw new BusinessException("模板不存在或无权操作");
        }

        // 1. 更新模板名称
        PriceTemplate template = new PriceTemplate();
        template.setId(templateId);
        template.setName(updateDTO.getName());
        this.updateById(template);

        // 2. 删除旧的配置项
        priceTemplateItemService.remove(new LambdaQueryWrapper<PriceTemplateItem>().eq(PriceTemplateItem::getTemplateId, templateId));

        // 3. 插入新的配置项
        if (!CollectionUtils.isEmpty(updateDTO.getItems())) {
            List<PriceTemplateItem> newItems = updateDTO.getItems().stream().map(itemDTO -> {
                PriceTemplateItem item = new PriceTemplateItem();
                BeanUtils.copyProperties(itemDTO, item);
                item.setTemplateId(templateId);
                return item;
            }).collect(Collectors.toList());
            priceTemplateItemService.saveBatch(newItems);
        }
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean deleteTemplate(Long templateId, Long operatorId) { // 修改点 7: 增加 operatorId
        if (templateId == null) {
            throw new BusinessException("模板ID不能为空");
        }

        if (operatorId.equals(0L)) {
            return this.removeById(templateId);
        }else {
            PriceTemplate existingTemplate = this.getById(templateId);
            if (existingTemplate == null || !existingTemplate.getCreatId().equals(operatorId)) {
                throw new BusinessException("模板不存在或无权操作");
            }
        }
        // 由于设置了外键级联删除，理论上只需要删除主表即可。
        return this.removeById(templateId);
    }
}