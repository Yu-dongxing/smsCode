package com.wzz.smscode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wzz.smscode.dto.EntityDTO.ProjectDTO;
import com.wzz.smscode.dto.ProjectPriceDetailsDTO;
import com.wzz.smscode.dto.ProjectPriceSummaryDTO;
import com.wzz.smscode.entity.Project;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ProjectService extends IService<Project> {

    @Transactional
    boolean createProject(ProjectDTO projectDTO);

    @Transactional
    boolean updateProject(ProjectDTO projectDTO);

    @Transactional
    boolean deleteProject(String projectId, Integer lineId);

    Project getProject(String projectId, Integer lineId);

    List<String> listLines(String projectId);

    Map<String, ProjectPriceDetailsDTO> getAllProjectPrices();

    Map<String, BigDecimal> fillMissingPrices(Map<String, BigDecimal> inputPrices);

    Map<String, ProjectPriceSummaryDTO> getAllProjectPriceSummaries();

    Boolean deleteByID(long id);
}
