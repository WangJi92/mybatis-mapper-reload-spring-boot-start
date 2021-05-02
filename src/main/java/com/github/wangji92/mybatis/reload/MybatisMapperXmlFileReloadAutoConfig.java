package com.github.wangji92.mybatis.reload;

import com.github.wangji92.mybatis.reload.core.MybatisMapperXmlFileReloadService;
import com.github.wangji92.mybatis.reload.core.MybatisMapperXmlFileWatchService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author 汪小哥
 * @date 02-05-2021
 */
@Configuration
@ConditionalOnClass(SqlSessionFactory.class)
@EnableConfigurationProperties(MybatisMapperXmlFileReloadProperties.class)
public class MybatisMapperXmlFileReloadAutoConfig {

    @Autowired
    private MybatisMapperXmlFileReloadProperties mybatisMapperXmlFileReloadProperties;

    /**
     * 启动对于mapper 文件的监听
     *
     * @param mybatisMapperXmlFileReloadService
     * @return
     */
    @Bean
    @ConditionalOnProperty(prefix = "mybatis.mapper.reload", value = "enable", havingValue = "true", matchIfMissing = false)
    public MybatisMapperXmlFileWatchService mybatisMapperXmlFileWatchService(@Autowired MybatisMapperXmlFileReloadService mybatisMapperXmlFileReloadService) {
        MybatisMapperXmlFileWatchService mapperFileWatchReload = new MybatisMapperXmlFileWatchService(mybatisMapperXmlFileReloadProperties.getMapperLocation());
        mapperFileWatchReload.setMybatisMapperXmlFileReloadService(mybatisMapperXmlFileReloadService);
        CompletableFuture.runAsync(mapperFileWatchReload::initWatchService);
        return mapperFileWatchReload;
    }

    @Bean
    public MybatisMapperXmlFileReloadService mybatisMapperXmlFileReloadService(@Autowired(required = false) List<SqlSessionFactory> sqlSessionFactoryList) {
        return new MybatisMapperXmlFileReloadService(sqlSessionFactoryList);
    }


}
