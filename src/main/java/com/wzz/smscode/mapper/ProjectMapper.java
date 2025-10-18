package com.wzz.smscode.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wzz.smscode.dto.ProjectPriceSummaryDTO;
import com.wzz.smscode.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    /**
     * 查询每个项目的价格摘要（最高售价的最大值和最低售价的最小值）
     * @return List<ProjectPriceSummaryDTO>
     */
    @Select("SELECT project_id, MAX(price_max) as maxPrice, MIN(price_min) as minPrice FROM project GROUP BY project_id")
    List<ProjectPriceSummaryDTO> selectProjectPriceSummaries();
}
