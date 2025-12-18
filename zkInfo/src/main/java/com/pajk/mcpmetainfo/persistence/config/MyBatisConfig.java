package com.pajk.mcpmetainfo.persistence.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * MyBatis 配置类
 * 
 * 通过 persistence.enabled 配置项控制持久化功能的启用
 * 
 * @author MCP Router Team
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(
    prefix = "mcp.persistence",
    name = "enabled",
    havingValue = "true"
)
@MapperScan("com.pajk.mcpmetainfo.persistence.mapper")
public class MyBatisConfig {
    
    private static final Logger log = LoggerFactory.getLogger(MyBatisConfig.class);
    
    /**
     * 配置 SqlSessionFactory
     * 支持下划线转驼峰等特性
     */
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);
        
        // 设置 Mapper XML 文件位置
        sessionFactory.setMapperLocations(
            new PathMatchingResourcePatternResolver()
                .getResources("classpath:mapper/**/*.xml")
        );
        
        // 设置类型别名包
        sessionFactory.setTypeAliasesPackage("com.pajk.mcpmetainfo.persistence.entity");
        
        // 设置 TypeHandler 包路径（重要：确保 TypeHandler 被正确加载）
        sessionFactory.setTypeHandlersPackage("com.pajk.mcpmetainfo.persistence.typehandler");
        
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setDefaultFetchSize(100);
        configuration.setDefaultStatementTimeout(30);
        
        sessionFactory.setConfiguration(configuration);
        
        SqlSessionFactory factory = sessionFactory.getObject();
        
        // 验证 TypeHandler 是否被正确注册
        if (factory != null) {
            org.apache.ibatis.session.Configuration config = factory.getConfiguration();
            log.info("MyBatis Configuration initialized. TypeHandlers registered: {}", 
                    config.getTypeHandlerRegistry().getTypeHandlers().size());
        }
        
        return factory;
    }
}

