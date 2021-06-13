package com.github.wangji92.mybatis.reload.core;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.binding.MapperRegistry;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.builder.xml.XMLMapperEntityResolver;
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
import java.util.HashSet;
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
    public boolean  reloadAllMapperXml(String mapperFilePath) {
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
            if (!this.reloadMapperXml(path, sqlSessionFactory)) {
                log.warn("reload  mapper file  {} fail", path.toString());
                result.set(false);
            }
        });
        return result.get();
    }


    public void reloadAllMapperClazz(String namespace) {
        if (CollectionUtils.isEmpty(sqlSessionFactoryList)) {
            return;
        }
        for (SqlSessionFactory sqlSessionFactory : sqlSessionFactoryList) {
            try {
                removeFromConfig(null, sqlSessionFactory.getConfiguration(), namespace);
                loadMapperClazz(sqlSessionFactory.getConfiguration(), namespace);
            } catch (Throwable e) {
                throw new BuilderException("relaod " + namespace + " error", e);
            }
        }
    }


    public boolean reloadMapperXml(Path watchPath, SqlSessionFactory sqlSessionFactory) {
        try (InputStream fis = Files.newInputStream(watchPath)) {
            Configuration configuration = sqlSessionFactory.getConfiguration();

            XPathParser context = new XPathParser(fis, true, configuration.getVariables(), new XMLMapperEntityResolver());
            XNode mapperNode = context.evalNode("/mapper");
            if (null == mapperNode) {
                return false;
            }
            String namespace = mapperNode.getStringAttribute("namespace");
            if (namespace == null || namespace.isEmpty()) {
                throw new BuilderException("Mapper's namespace cannot be empty");
            }

            //remove parsed xml info from configuration
            removeFromConfig(watchPath, configuration, namespace);

            //load xml info into configuration
            loadMapperXml(configuration, watchPath);

            //load mapper interface
            loadMapperClazz(configuration, namespace);
            return true;
        } catch (Throwable e) {
            log.error("load {} fail", watchPath.toString(), e);
        }
        return false;
    }


    private void removeFromConfig(Path watchPath, Configuration configuration, String namespace) throws ClassNotFoundException {
        Set<String> loadedResources = (Set<String>) this.getFieldValue(configuration, "loadedResources");
        if (watchPath != null) {
            //如果直接监听线上环境的配置文件路径（如果watcher监听的是外部路径，则显得多余），并使用了mybatis-spring插件，这是必须的
            loadedResources.remove(watchPath.toString());
        }
        //针对没有使用mybatis-sring插件的情况
        loadedResources.remove(namespace.replace('.', '/') + ".xml");

        //针对无xml，使用纯注解形式
        loadedResources.remove(Class.forName(namespace).toString());
        //针对无xml，使用纯注解形式, 貌似与上一句语句等价的
        loadedResources.remove(namespace);
        //针对无xml，使用纯注解形式
        loadedResources.remove("namespace:" + namespace);


        Map<String, ?> sqlFragments = ((Map<String, ?>) this.getFieldValue(configuration, "sqlFragments"));
        Map<String, ?> parameterMaps = (Map<String, ?>) this.getFieldValue(configuration, "parameterMaps");
        Map<String, ?> resultMaps = ((Map<String, ?>) this.getFieldValue(configuration, "resultMaps"));
        Map<String, ?> mappedStatements = ((Map<String, ?>) this.getFieldValue(configuration, "mappedStatements"));
        Map<String, ?> caches = ((Map<String, ?>) this.getFieldValue(configuration, "caches"));
        Map<String, ?> keyGenerators = ((Map<String, ?>) this.getFieldValue(configuration, "keyGenerators"));

        removeKeyStartWith(sqlFragments, namespace);
        removeKeyStartWith(parameterMaps, namespace);
        removeKeyStartWith(resultMaps, namespace);
        removeKeyStartWith(mappedStatements, namespace);
        caches.remove(namespace); //一个mapper可配置一个cache
        removeKeyStartWith(keyGenerators, namespace);
    }


    /**
     * 加载新的mapper 文件
     *
     * @param configuration
     * @param watchPath
     */
    private void loadMapperXml(Configuration configuration, Path watchPath) throws IOException {
        try (InputStream fileInputStream = Files.newInputStream(watchPath)) {
            XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(fileInputStream, configuration,
                    watchPath.toString(),
                    configuration.getSqlFragments());
            xmlMapperBuilder.parse();
        }
    }

    private void loadMapperClazz(Configuration configuration, String namespace) throws ClassNotFoundException {
        Class<?> clazz = Class.forName(namespace);
        // remove old mapper class
        MapperRegistry mapperRegistry = (MapperRegistry) getFieldValue(configuration, "mapperRegistry");
        Map<Class<?>, ?> sqlFragments = ((Map<Class<?>, ?>) this.getFieldValue(mapperRegistry, "knownMappers"));
        sqlFragments.remove(clazz);
        //readd mapper class
        configuration.addMapper(clazz);
    }


    private Object getFieldValue(Object target, String name) {
        Field field = ReflectionUtils.findField(target.getClass(), name);
        ReflectionUtils.makeAccessible(field);
        return ReflectionUtils.getField(field, target);
    }


    public static void removeKeyStartWith(Map<String, ?> map, String nameSpace) {
        if (map == null || map.size() == 0) {
            return;
        }
        HashSet<String> keys = new HashSet<>(map.keySet());//避免并发修改异常
        while (nameSpace.endsWith(".")) {
            nameSpace = nameSpace.substring(0, nameSpace.length() - 1);
        }
        String nameSpaceWithDot = nameSpace + ".";
        for (String key : keys) {
            if (key.startsWith(nameSpaceWithDot)) {
                map.remove(key);
            }
        }
    }

}
