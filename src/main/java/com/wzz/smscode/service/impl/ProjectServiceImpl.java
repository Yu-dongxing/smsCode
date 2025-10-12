package com.wzz.smscode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wzz.smscode.dto.ProjectPriceSummaryDTO;
import com.wzz.smscode.entity.Project;
import com.wzz.smscode.exception.BusinessException;
import com.wzz.smscode.mapper.ProjectMapper;
import com.wzz.smscode.service.ProjectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements ProjectService {

    // Spring会自动注入 baseMapper，它就是 ProjectMapper 的实例
    // @Autowired private ProjectMapper projectMapper; // 此行可省略

    @Autowired
    private ObjectMapper objectMapper; // 引入Jackson库用于JSON操作

    @Transactional
    @Override
    public boolean addProjectLine(Project project) {
        // 1. 验证 (project_id, line_id) 组合是否唯一
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getProjectId, project.getProjectId())
                .eq(Project::getLineId, project.getLineId());

        if (this.baseMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(
                    String.format("项目线路已存在: projectId=%s, lineId=%s", project.getProjectId(), project.getLineId())
            );
        }

        // 2. 插入数据
        return this.save(project);
    }

    @Override
    public List<Project> listLinesByProjectId(String projectId) {
        return this.list(new LambdaQueryWrapper<Project>().eq(Project::getProjectId, projectId));
    }

    @Override
    public Map<String, ProjectPriceSummaryDTO> getAllProjectPriceSummaries() {
        List<ProjectPriceSummaryDTO> summaries = this.baseMapper.selectProjectPriceSummaries();
        // 将列表转换为Map，方便按projectId快速查找
        return summaries.stream()
                .collect(Collectors.toMap(ProjectPriceSummaryDTO::getProjectId, Function.identity()));
    }

    @Override
    public String completeUserPrices(String userPricesJson) {
        try {
            // 1. 解析用户传入的JSON为Map
            Map<String, BigDecimal> userPrices = objectMapper.readValue(userPricesJson, new TypeReference<Map<String, BigDecimal>>() {});

            // 2. 获取所有项目的价格摘要信息（包含最高价）
            Map<String, ProjectPriceSummaryDTO> priceSummaries = getAllProjectPriceSummaries();

            // 3. 获取系统中所有的项目线路
            List<Project> allProjectLines = this.list();

            boolean isModified = false;
            // 4. 遍历所有线路，检查并补全
            for (Project line : allProjectLines) {
                String priceKey = line.getProjectId() + "-" + line.getLineId();
                if (!userPrices.containsKey(priceKey)) {
                    // 5. 如果缺失，找到该线路所属项目的最高价进行填充
                    ProjectPriceSummaryDTO summary = priceSummaries.get(line.getProjectId());
                    if (summary != null && summary.getMaxPrice() != null) {
                        log.info("价格补全: 线路'{}'缺失, 使用项目'{}'的最高价'{}'进行填充。", priceKey, line.getProjectId(), summary.getMaxPrice());
                        userPrices.put(priceKey, summary.getMaxPrice());
                        isModified = true;
                    } else {
                        // 异常处理：如果某个项目没有线路或者价格信息，这可能是一个数据问题
                        log.warn("价格补全警告: 线路'{}'所属的项目'{}'没有找到价格摘要信息，无法进行价格补全。", priceKey, line.getProjectId());
                    }
                }
            }

            // 6. 如果Map被修改过，则序列化为新的JSON字符串返回，否则返回原字符串
            return isModified ? objectMapper.writeValueAsString(userPrices) : userPricesJson;

        } catch (IOException e) {
            log.error("解析用户价格JSON失败: " + userPricesJson, e);
            // 根据业务需求，可以抛出自定义异常或返回原始JSON
            throw new RuntimeException("无效的用户价格JSON格式", e);
        }
    }
}