package com.wzz.smscode.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Hutool JSONObject 与 数据库 VARCHAR/TEXT/JSON 类型的转换器
 */
@MappedJdbcTypes(JdbcType.VARCHAR) // 声明这是为数据库的VARCHAR类型服务的
@MappedTypes(JSONObject.class)     // 声明这是为Java的JSONObject类型服务的
public class HutoolJsonObjectTypeHandler extends BaseTypeHandler<JSONObject> {

    /**
     * 将Java的JSONObject类型参数，设置到PreparedStatement中
     * @param ps PreparedStatement对象
     * @param i 参数位置
     * @param parameter JSONObject参数
     * @param jdbcType jdbc类型
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, JSONObject parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.toString());
    }

    /**
     * 从ResultSet中根据列名获取值，并转换为JSONObject
     */
    @Override
    public JSONObject getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        return StrUtil.isBlank(json) ? null : JSONUtil.parseObj(json);
    }

    /**
     * 从ResultSet中根据列索引获取值，并转换为JSONObject
     */
    @Override
    public JSONObject getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String json = rs.getString(columnIndex);
        return StrUtil.isBlank(json) ? null : JSONUtil.parseObj(json);
    }

    /**
     * 从CallableStatement中获取值，并转换为JSONObject
     */
    @Override
    public JSONObject getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String json = cs.getString(columnIndex);
        return StrUtil.isBlank(json) ? null : JSONUtil.parseObj(json);
    }
}