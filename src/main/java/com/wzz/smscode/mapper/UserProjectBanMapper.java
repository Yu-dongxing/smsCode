package com.wzz.smscode.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.wzz.smscode.dto.UserProjectBanQueryDTO;
import com.wzz.smscode.dto.UserProjectBanResponseDTO;
import com.wzz.smscode.entity.UserProjectBan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserProjectBanMapper extends BaseMapper<UserProjectBan> {

    @Select("<script>" +
            "SELECT " +
            "  b.id, " +
            "  b.user_id AS userId, " +
            "  u.user_name AS userName, " +
            "  b.project_id AS projectId, " +
            "  b.line_id AS lineId, " +
            "  p.project_name AS projectName, " +
            "  p.line_name AS lineName, " +
            "  b.ban_time AS banTime, " +
            "  b.unban_time AS unbanTime, " +
            "  b.status, " +
            "  b.trigger_attempts AS triggerAttempts, " +    // 新增字段映射
            "  b.trigger_successes AS triggerSuccesses, " +  // 新增字段映射
            "  b.trigger_rate AS triggerRate " +            // 新增字段映射
            "FROM user_project_ban b " +
            "LEFT JOIN user u ON b.user_id = u.id " +
            "LEFT JOIN project p ON b.project_id = p.project_id AND b.line_id = p.line_id " +
            "<where> " +
            "  b.status = 0 AND b.unban_time > NOW() " +
            "  <if test='query.userName != null and query.userName != \"\"'> " +
            "    AND u.user_name LIKE CONCAT('%', #{query.userName}, '%') " +
            "  </if> " +
            "  <if test='query.projectName != null and query.projectName != \"\"'> " +
            "    AND p.project_name LIKE CONCAT('%', #{query.projectName}, '%') " +
            "  </if> " +
            "  <if test='query.lineName != null and query.lineName != \"\"'> " +
            "    AND p.line_name LIKE CONCAT('%', #{query.lineName}, '%') " +
            "  </if> " +
            "  <if test='query.projectId != null and query.projectId != \"\"'> " +
            "    AND b.project_id = #{query.projectId} " +
            "  </if> " +
            "  <if test='query.lineId != null'> " +
            "    AND b.line_id = #{query.lineId} " +
            "  </if> " +
            "</where> " +
            "ORDER BY b.ban_time DESC" +
            "</script>")
    IPage<UserProjectBanResponseDTO> selectBanPage(IPage<UserProjectBanResponseDTO> page, @Param("query") UserProjectBanQueryDTO query);
}