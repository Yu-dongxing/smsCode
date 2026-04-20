package com.wzz.smscode.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wzz.smscode.entity.NumberRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface NumberRecordMapper extends BaseMapper<NumberRecord> {

    @Select("SELECT * FROM number_record WHERE id = #{id} FOR UPDATE")
    NumberRecord selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            <script>
            SELECT
                a.id AS agentId,
                a.user_name AS agentName,
                COUNT(DISTINCT u.id) AS subUserCount,
                COUNT(nr.id) AS totalNumbers,
                SUM(CASE WHEN nr.status = 2 THEN 1 ELSE 0 END) AS successCount,
                COALESCE(SUM(CASE WHEN nr.charged = 1 THEN nr.price ELSE 0 END), 0) AS totalRevenue,
                COALESCE(SUM(CASE WHEN nr.charged = 1 THEN nr.cost_price ELSE 0 END), 0) AS totalCost
            FROM `user` a
            JOIN `user` u ON u.parent_id = a.id
            JOIN number_record nr ON nr.user_id = u.id
            WHERE a.is_agent = 1
                AND nr.get_number_time &gt;= #{startTime}
                AND nr.get_number_time &lt;= #{endTime}
                <if test="agentId != null">
                    AND a.id = #{agentId}
                </if>
                <if test="agentName != null and agentName != ''">
                    AND a.user_name LIKE CONCAT('%', #{agentName}, '%')
                </if>
                <if test="projectId != null and projectId != ''">
                    AND nr.project_id = #{projectId}
                </if>
                <if test="lineId != null">
                    AND nr.line_id = #{lineId}
                </if>
            GROUP BY a.id, a.user_name
            ORDER BY totalNumbers DESC
            </script>
            """)
    IPage<Map<String, Object>> selectAgentDailyStatsPage(
            Page<Map<String, Object>> page,
            @Param("agentId") Long agentId,
            @Param("agentName") String agentName,
            @Param("projectId") String projectId,
            @Param("lineId") Integer lineId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
}
