package com.dboat.iot.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类
 * <p>
 * 配置 MyBatis-Plus 核心功能：
 * <ul>
 *   <li>Mapper 接口扫描：自动扫描 com.dboat.iot.mapper（IoT 设备域）与 com.dboat.user.mapper（用户认证域）两个包下的 Mapper 接口</li>
 *   <li>分页插件：注册 MySQL 分页拦截器，支持 IPage 分页查询</li>
 * </ul>
 * </p>
 *
 * @author dboat
 */
@Configuration
@MapperScan({"com.dboat.iot.mapper", "com.dboat.user.mapper"})
public class MyBatisPlusConfig {

    /**
     * 注册 MyBatis-Plus 插件拦截器
     * <p>
     * 当前注册了分页插件，指定数据库类型为 MySQL。
     * 如需添加其他插件（如乐观锁、防全表更新等），在此方法中追加即可。
     * </p>
     *
     * @return MybatisPlusInterceptor 拦截器实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 注册 MySQL 分页插件，支持 Page/IPage 分页查询
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
