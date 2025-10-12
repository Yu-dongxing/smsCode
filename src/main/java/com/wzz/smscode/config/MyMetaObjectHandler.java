package com.wzz.smscode.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        // 指定使用 "Asia/Shanghai" 时区获取当前时间
        LocalDateTime beijingTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));

        // 严格填充（找到属性就填充，不会覆盖已有的值）
        // setFieldValByName 如果实体已有值，仍会覆盖，这里用 strictInsertFill
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, beijingTime);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, beijingTime);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 指定使用 "Asia/Shanghai" 时区获取当前时间
        LocalDateTime beijingTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));

        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, beijingTime);
    }
}