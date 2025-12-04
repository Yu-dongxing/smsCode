package com.wzz.smscode.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wzz.smscode.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据用户ID查询并施加行级排他锁（悲观锁）
     * @param id 用户ID
     * @return User 锁定的用户实体
     */
    @Select("SELECT * FROM user WHERE id = #{id} FOR UPDATE")
    User selectByIdForUpdate(Long id);


    /**
     * 根据用户名查询并施加行级排他锁（悲观锁）
     * 用于登录更新时间、修改密码等场景
     */
    @Select("SELECT * FROM user WHERE user_name = #{userName} FOR UPDATE")
    User selectByUserNameForUpdate(@Param("userName") String userName);
}
