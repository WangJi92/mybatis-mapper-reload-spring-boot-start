package com.github.wangji92.mybatis.reload.core;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * mapper xml 文件重新加载
 *
 * @author 汪小哥
 * @date 01-05-2021
 */
@Slf4j
public class MybatisMapperXmlFileReloadService {

    private List<SqlSessionFactory> sqlSessionFactoryList;


    public MybatisMapperXmlFileReloadService(List<SqlSessionFactory> sqlSessionFactoryList) {
        this.sqlSessionFactoryList = sqlSessionFactoryList;
    }

    /**
     * 重新 加载mapper xml  【这里可以使用arthas 进行调用远程增强】
     * eg  ognl -x 3 '#springContext=@com.wangji92.arthas.plugin.demo.common.ApplicationContextProvider@context,#springContext.getBean("mybatisMapperXmlFileReloadService").reloadAllSqlSessionFactoryMapper("/root/xxx.xml")' -c xxx
     *
     * @param mapperFilePath
     * @return
     */
    public boolean reloadAllSqlSessionFactoryMapper(String mapperFilePath) {
        if (CollectionUtils.isEmpty(sqlSessionFactoryList)) {
            log.warn("not find SqlSessionFactory bean");
            return false;
        }

        Path path = Paths.get(mapperFilePath);
        if (!Files.exists(path)) {
            log.warn("mybatis reload mapper xml not exist ={}", mapperFilePath);
            return false;
        }

        AtomicBoolean result = new AtomicBoolean(true);

        // 删除mapper 缓存 重新加载
        sqlSessionFactoryList.parallelStream().forEach(sqlSessionFactory -> {
            Configuration configuration = sqlSessionFactory.getConfiguration();
            if (!this.removeMapperCacheAndReloadNewMapperFile(path, configuration)) {
                log.warn("reload new mapper file fail {}", path.toString());
                result.set(false);
            }
        });
        return result.get();
    }


    private Object readField(Object target, String name) {
        Field field = ReflectionUtils.findField(target.getClass(), name);
        ReflectionUtils.makeAccessible(field);
        return ReflectionUtils.getField(field, target);
    }

    /**
     * 删除老的mapper 缓存 加载新的mapper 文件
     *
     * @param watchPath
     * @param configuration
     * @return
     */
    private boolean removeMapperCacheAndReloadNewMapperFile(Path watchPath, Configuration configuration) {
        try (InputStream fileInputStream = Files.newInputStream(watchPath)) {
            XPathParser context = new XPathParser(fileInputStream, true);
            XNode contextNode = context.evalNode("/mapper");
            if (null == contextNode) {
                return false;
            }
            String namespace = contextNode.getStringAttribute("namespace");
            if (namespace == null || namespace.isEmpty()) {
                throw new BuilderException("Mapper's namespace cannot be empty");
            }

            this.removeOldMapperFileConfigCache(configuration, contextNode, namespace);
            this.addNewMapperFile(configuration, watchPath, namespace);
            return true;
        } catch (Exception e) {
            log.warn("load fail {}", watchPath.toString(), e);
        }
        return false;
    }

    /**
     * 删除老的mapper 相关的配置文件
     *
     * @param configuration
     * @param mapper
     * @param namespace
     * @see XMLMapperBuilder#configurationElement
     */
    private void removeOldMapperFileConfigCache(Configuration configuration, XNode mapper, String namespace) {
        String xmlResource = namespace.replace('.', '/') + ".xml";
        ((Set<?>) this.readField(configuration, "loadedResources")).remove(xmlResource);
        for (XNode node : mapper.evalNodes("parameterMap")) {
            String parameterMapId = this.resolveId(namespace, node.getStringAttribute("id"));
            ((Map<?, ?>) this.readField(configuration, "parameterMaps")).remove(parameterMapId);
        }
        for (XNode node : mapper.evalNodes("resultMap")) {
            String resultMapId = this.resolveId(namespace, node.getStringAttribute("id"));
            ((Map<?, ?>) this.readField(configuration, "resultMaps")).remove(resultMapId);
        }
        for (XNode node : mapper.evalNodes("sql")) {
            String sqlId = this.resolveId(namespace, node.getStringAttribute("id"));
            ((Map<?, ?>) this.readField(configuration, "sqlFragments")).remove(sqlId);
        }
        for (XNode node : mapper.evalNodes("select|insert|update|delete")) {
            String statementId = this.resolveId(namespace, node.getStringAttribute("id"));
            ((Map<?, ?>) this.readField(configuration, "mappedStatements")).remove(statementId);
        }
    }

    /**
     * 加载新的mapper 文件
     *
     * @param configuration
     * @param watchPath
     * @param namespace
     */
    private void addNewMapperFile(Configuration configuration, Path watchPath, String namespace) throws IOException {
        try (InputStream fileInputStream = Files.newInputStream(watchPath)) {
            String xmlResource = namespace.replace('.', '/') + ".xml";
            XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(fileInputStream, configuration,
                    xmlResource,
                    configuration.getSqlFragments());
            xmlMapperBuilder.parse();
        }

    }


    private String resolveId(String namespace, String id) {
        return namespace + "." + id;
    }
}
