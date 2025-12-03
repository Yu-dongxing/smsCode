package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wzz.smscode.dto.PriceTemplateCreateDTO;
import com.wzz.smscode.dto.PriceTemplateItemDTO;
import com.wzz.smscode.dto.PriceTemplateResponseDTO;
import com.wzz.smscode.entity.*;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.mapper.PriceTemplateItemMapper;
import com.wzz.smscode.mapper.PriceTemplateMapper;
import com.wzz.smscode.service.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
public class PriceTemplateServiceImpl extends ServiceImpl<PriceTemplateMapper, PriceTemplate> implements PriceTemplateService {

    @Autowired
    private PriceTemplateItemService priceTemplateItemService;

    @Autowired
    private UserProjectLineService userProjectLineService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    @Lazy
    private UserService userService;


    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean saveOrUpdateTemplate(PriceTemplateCreateDTO dto, Long operatorId) {
        boolean isAdmin = (operatorId == 0L);
        User agent = isAdmin ? null : userService.getById(operatorId);

        // 1. 获取操作员自己的价格配置（如果是代理）
        Map<String, BigDecimal> agentCostMap = new HashMap<>();
        if (!isAdmin) {
            if(agent == null || agent.getIsAgent() != 1) throw new BusinessException("无权操作");
            // 代理的成本来自于他自己的模板
            Long agentTemplateId = agent.getTemplateId();
            List<PriceTemplateItem> agentItems = priceTemplateItemService.list(new LambdaQueryWrapper<PriceTemplateItem>()
                    .eq(PriceTemplateItem::getTemplateId, agentTemplateId));
            agentCostMap = agentItems.stream().collect(Collectors.toMap(
                    k -> k.getProjectId() + "-" + k.getLineId(),
                    PriceTemplateItem::getPrice
            ));
        }

        // 2. 处理模板主体
        PriceTemplate template = new PriceTemplate();
        if (dto.getId() != null) {
            template = this.getById(dto.getId());
            if (template == null) throw new BusinessException("模板不存在");
            if (!template.getCreatId().equals(operatorId)) throw new BusinessException("无权修改他人模板");
        } else {
            template.setCreatId(operatorId);
            template.setUserIds(""); // 初始化
        }
        template.setName(dto.getName());
        this.saveOrUpdate(template);

        // 3. 处理模板项 (全量覆盖或增量更新，这里简化为删除旧的插入新的，实际生产建议做Diff)
        // 为了保持ID稳定，这里建议做 upsert，以下为简化逻辑：
        if(dto.getId() != null){
            priceTemplateItemService.remove(new LambdaQueryWrapper<PriceTemplateItem>().eq(PriceTemplateItem::getTemplateId, template.getId()));
        }

        List<Project> allProjects = projectService.list();
        Map<String, Project> projectMap = allProjects.stream().collect(Collectors.toMap(
                k -> k.getProjectId() + "-" + k.getLineId(), v -> v
        ));

        List<PriceTemplateItem> itemsToSave = new ArrayList<>();

        for (PriceTemplateItemDTO itemDto : dto.getItems()) {
            String key = itemDto.getProjectId() + "-" + itemDto.getLineId();
            Project sysProject = projectMap.get(key);
            if(sysProject == null || !sysProject.isStatus()) continue; // 跳过无效项目

            BigDecimal setPrice = itemDto.getPrice();
            BigDecimal costPrice;
            BigDecimal minAllowed;
            BigDecimal maxAllowed = sysProject.getPriceMax(); // 系统最高价

            if (isAdmin) {
                // 管理员：成本 = 系统成本，最低价 = 系统最低价
                costPrice = sysProject.getCostPrice();
                minAllowed = sysProject.getCostPrice(); // 管理员售价不能低于成本
            } else {
                // 代理：成本 = 代理自己的进货价
                if (!agentCostMap.containsKey(key)) {
                    throw new BusinessException("代理自身未配置项目 " + key + "，无法创建下级模板");
                }
                costPrice = agentCostMap.get(key);
                minAllowed = costPrice; // 代理售价不能低于自己的成本
            }

            // 价格校验
            if (setPrice.compareTo(minAllowed) < 0) {
                throw new BusinessException("项目 " + sysProject.getProjectName() + " 设置价格低于允许的最低价：" + minAllowed);
            }
            if (setPrice.compareTo(maxAllowed) > 0) {
                throw new BusinessException("项目 " + sysProject.getProjectName() + " 设置价格高于系统最高价：" + maxAllowed);
            }

            PriceTemplateItem item = new PriceTemplateItem();
            item.setTemplateId(template.getId());
            item.setProjectTableId(sysProject.getId());
            item.setProjectId(Long.valueOf(sysProject.getProjectId()));
            item.setLineId(Long.valueOf(sysProject.getLineId()));
            item.setProjectName(sysProject.getProjectName());
            item.setPrice(setPrice);
            item.setCostPrice(costPrice);
            item.setMaxPrice(maxAllowed);
            item.setMinPrice(minAllowed);
            itemsToSave.add(item);
        }

        priceTemplateItemService.saveBatch(itemsToSave);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean createTemplate(PriceTemplateCreateDTO createDTO, Long creatorId) {
        if (createDTO == null || createDTO.getName() == null || createDTO.getName().trim().isEmpty()) {
            throw new BusinessException("模板名称不能为空");
        }
        //如果是管理员操作那么这个id始终为0
        if (creatorId != null && !creatorId.equals(0L)) {
            List<PriceTemplateItemDTO> itemsToCreate = createDTO.getItems();
            if (!CollectionUtils.isEmpty(itemsToCreate)) {

                // 1. 获取代理自己的所有价格配置，存入Map
                Map<String, BigDecimal> agentPriceMap = userProjectLineService.getLinesByUserId(creatorId).stream()
                        .collect(Collectors.toMap(
                                line -> line.getProjectId() + ":" + line.getLineId(),
                                UserProjectLine::getAgentPrice,
                                (existing, replacement) -> existing
                        ));

                // 2. 【优化】一次性获取所有项目配置(包含priceMin和priceMax)，存入Map
                // Value 直接存 Project 对象，方便后续取值
                Map<String, Project> projectConfigMap = projectService.list().stream()
                        .collect(Collectors.toMap(
                                project -> project.getProjectId() + ":" + project.getLineId(),
                                Function.identity(), // Value是Project对象本身
                                (existing, replacement) -> existing
                        ));


                // 3. 遍历模板中的每一个项目，进行双重价格校验
                for (PriceTemplateItemDTO item : itemsToCreate) {
                    String key = item.getProjectId() + ":" + item.getLineId();
                    BigDecimal templatePrice = item.getPrice();

                    if (templatePrice == null) {
                        throw new BusinessException("项目 '" + item.getProjectName() + "' 的价格不能为空");
                    }

                    // --- 校验一：模板价格不能低于最低价 ---
                    BigDecimal minimumAllowedPrice;
                    String minPriceSourceName;

                    if (agentPriceMap.containsKey(key)) {
                        minimumAllowedPrice = agentPriceMap.get(key);
                        minPriceSourceName = "您的配置价";
                    } else {
                        Project projectConfig = projectConfigMap.get(key);
                        // 检查项目配置是否存在，并且设置了最低价
                        if (projectConfig != null && projectConfig.getPriceMin() != null) {
                            minimumAllowedPrice = projectConfig.getPriceMin();
                            minPriceSourceName = "平台项目最低价";
                        } else {
                            throw new BusinessException("无法为项目 '" + item.getProjectName() + "' 定价，缺少您的价格配置或平台未设置项目最低价。");
                        }
                    }

                    if (templatePrice.compareTo(minimumAllowedPrice) < 0) {
                        throw new BusinessException(
                                "项目 '" + item.getProjectName() + "' 的模板价格(" + templatePrice +
                                        ")不能低于" + minPriceSourceName + "(" + minimumAllowedPrice + ")。"
                        );
                    }

                    // --- 【新增】校验二：模板价格不能高于最高价 ---
                    Project projectConfig = projectConfigMap.get(key);
                    // 检查项目配置是否存在，并且设置了最高价 (priceMax > 0 才有效)
                    if (projectConfig != null && projectConfig.getPriceMax() != null && projectConfig.getPriceMax().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal maximumAllowedPrice = projectConfig.getPriceMax();

                        if (templatePrice.compareTo(maximumAllowedPrice) > 0) {
                            throw new BusinessException(
                                    "项目 '" + item.getProjectName() + "' 的模板价格(" + templatePrice +
                                            ")不能高于项目允许的最高价(" + maximumAllowedPrice + ")。"
                            );
                        }
                    }
                }
            }
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
                itemDTO.setProjectId(String.valueOf(item.getProjectId()));
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
    @Autowired private PriceTemplateItemMapper itemMapper;


    @Override
    public PriceTemplateItem getPriceConfig(Long templateId, String projectId, Integer lineId) {
        return itemMapper.selectOne(new LambdaQueryWrapper<PriceTemplateItem>()
                .eq(PriceTemplateItem::getTemplateId, templateId)
                .eq(PriceTemplateItem::getProjectId, projectId)
                .eq(PriceTemplateItem::getLineId, lineId)
                .last("LIMIT 1")
        );
    }

    @Override
    public void addUserToTemplate(Long templateId, Long userId) {
        PriceTemplate template = this.getById(templateId);
        if (template == null) return;

        String ids = template.getUserIds();
        if (!StringUtils.hasText(ids)) {
            ids = userId.toString();
        } else {
            // 简单排重
            List<String> idList = new ArrayList<>(Arrays.asList(ids.split(",")));
            if (!idList.contains(userId.toString())) {
                idList.add(userId.toString());
                ids = String.join(",", idList);
            }
        }
        template.setUserIds(ids);
        this.updateById(template);
    }

    @Override
    public void removeUserFromTemplate(Long templateId, Long userId) {
        PriceTemplate template = this.getById(templateId);
        if (template == null || !StringUtils.hasText(template.getUserIds())) return;

        List<String> idList = new ArrayList<>(Arrays.asList(template.getUserIds().split(",")));
        if (idList.remove(userId.toString())) {
            template.setUserIds(String.join(",", idList));
            this.updateById(template);
        }
    }
    @Override
    public List<PriceTemplateItem> getTemplateItems(Long templateId) {
        return itemMapper.selectList(new LambdaQueryWrapper<PriceTemplateItem>()
                .eq(PriceTemplateItem::getTemplateId, templateId)
                .orderByAsc(PriceTemplateItem::getProjectId) // 可选：按项目ID排序
        );
    }
}