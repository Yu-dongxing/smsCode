package com.wzz.smscode.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wzz.smscode.entity.NumberRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NumberRecordMapper extends BaseMapper<NumberRecord> {

    @Select("SELECT * FROM number_record WHERE id = #{id} FOR UPDATE")
    NumberRecord selectByIdForUpdate(@Param("id") Long id);

}
