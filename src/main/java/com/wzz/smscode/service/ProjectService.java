package com.wzz.smscode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wzz.smscode.dto.ProjectPriceSummaryDTO;
import com.wzz.smscode.entity.Project;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

public interface ProjectService extends IService<Project> {
    @Transactional
    boolean addProjectLine(Project project);

    List<Project> listLinesByProjectId(String projectId);

    Map<String, ProjectPriceSummaryDTO> getAllProjectPriceSummaries();

    String completeUserPrices(String userPricesJson);
}
