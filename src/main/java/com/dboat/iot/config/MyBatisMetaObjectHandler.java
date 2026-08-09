package com.dboat.iot.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器
 * <p>
 * 在实体对象执行 INSERT / UPDATE 时，自动填充 createTime 和 updateTime 字段。
 * 配合实体类中的 @TableField(fill = FieldFill.INSERT) 等注解使用，
 * 避免每次手动设置时间字段，保证时间格式统一。
 * </p>
 *
 * @author dboat
 */
@Component
public class MyBatisMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时自动填充 createTime 和 updateTime
     *
     * @param metaObject 元对象（包含待插入的实体信息）
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        // 填充创建时间（仅在字段为 null 时填充）
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        // 填充更新时间（新记录也设置当前时间）
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    /**
     * 更新时自动填充 updateTime
     *
     * @param metaObject 元对象（包含待更新的实体信息）
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        // 每次更新时自动刷新更新时间
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
