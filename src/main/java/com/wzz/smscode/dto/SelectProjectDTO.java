package com.wzz.smscode.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 项目数据传输对象
 */
@Data
@EqualsAndHashCode // 用于后续Stream去重
@NoArgsConstructor
@AllArgsConstructor
public class SelectProjectDTO {

    /**
     * 项目ID
     */
    private String projectId;

    /**
     * 项目名称
     */
    private String projectName;
}