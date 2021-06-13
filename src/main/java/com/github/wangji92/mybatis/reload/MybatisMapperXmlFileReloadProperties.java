package com.github.wangji92.mybatis.reload;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author 汪小哥
 * @date 01-05-2021
 */
@Configuration
@ConfigurationProperties(prefix = "mybatis.mapper.reload")
public class MybatisMapperXmlFileReloadProperties {

    public MybatisMapperXmlFileReloadProperties(boolean enable, String mapperLocation) {
        this.enable = enable;
        this.mapperLocation = mapperLocation;
    }

    public MybatisMapperXmlFileReloadProperties() {
    }

    /**
     * 是否开启
     */
    private boolean enable = false;
    /**
     * Locations of MyBatis mapper files  检查的mapper的路径
     */
    private String mapperLocation = "classpath*:/**/*apper*.xml";

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public String getMapperLocation() {
        return mapperLocation;
    }

    public void setMapperLocation(String mapperLocation) {
        this.mapperLocation = mapperLocation;
    }
}
