package com.github.wangji92.mybatis.reload.core;

import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryWatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.CollectionUtils;
import org.springframework.util.PatternMatchUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * mybatis xml 文件监听 重新加载
 *
 * @author 汪小哥
 * @date 01-05-2021
 */
@Slf4j
public class MybatisMapperXmlFileWatchService implements DisposableBean {

    /**
     * 查询mapper的file匹配路径 eg: classpath*:*mapper*.xml
     */
    private String mybatisMapperFileLocationPattern;

    /**
     * 计数器
     */
    private AtomicInteger reloadCount = new AtomicInteger(0);

    /**
     * xml 文件
     */
    private static String XML_PATTERN = "*.xml";

    /**
     * mybatis mapper xml 重新加载服务
     */
    private MybatisMapperXmlFileReloadService mybatisMapperXmlFileReloadService;

    /**
     * 文件监听服务
     */
    private DirectoryWatcher watcher;

    /**
     * mybatis  mapper xml 的文件路径
     */
    private Resource[] mapperLocations;


    public MybatisMapperXmlFileWatchService(String mybatisMapperFileLocationPattern) {
        this.mybatisMapperFileLocationPattern = mybatisMapperFileLocationPattern;
    }

    /**
     * 启动监听
     */
    public void initWatchService() {
        log.info("init mybatis mapper reload service begin");
        if (watcher != null) {
            return;
        }
        this.scanMapperXml();
        Set<Path> mapperXmlFileDirPaths = this.getWatchMapperXmlFileDirPaths();
        if (CollectionUtils.isEmpty(mapperXmlFileDirPaths)) {
            log.warn("not found mapper xml in {}", mybatisMapperFileLocationPattern);
            return;
        }
        this.startMapperWatchService(mapperXmlFileDirPaths);
        log.info("init mybatis mapper reload service success");

    }

    /**
     * 启动监听 mapper xml 变化的服务
     *
     * @param mapperXmlFileDirPaths
     * @throws IOException
     */
    private void startMapperWatchService(Set<Path> mapperXmlFileDirPaths) {
        /**
         * https://github.com/gmethvin/directory-watcher
         */
        try {
            watcher = DirectoryWatcher.builder()
                    .paths(new ArrayList<>(mapperXmlFileDirPaths))
                    .listener(event -> {
                        if (DirectoryChangeEvent.EventType.MODIFY.equals(event.eventType())) {
                            if (event.isDirectory()) {
                                return;
                            }
                            Path path = event.path();
                            String fullPath = path.toString();
                            if (!PatternMatchUtils.simpleMatch(XML_PATTERN,
                                    fullPath)) {
                                return;
                            }
                            boolean result = mybatisMapperXmlFileReloadService.reloadAllSqlSessionFactoryMapper(fullPath);
                            log.info("path={} atomicInteger ={} result={}", path, reloadCount.incrementAndGet(), result);
                        }
                    })
                    .fileHashing(true)
                    .build();

            Thread watchThread = new Thread(this::runWatchService, "WatchService mybatis mapper xml reload");
            watchThread.setDaemon(true);
            watchThread.start();
        } catch (Exception e) {
            log.error("startMapperWatchService error", e);
        }
    }

    /**
     * 获取需要被监听的 mapper的父文件dir路径
     *
     * @return
     */
    private Set<Path> getWatchMapperXmlFileDirPaths() {
        Resource[] resources = this.mapperLocations;
        if (resources.length == 0) {
            return Collections.emptySet();
        }
        Set<Path> parentDirSet = new HashSet<>(5);
        for (Resource resource : resources) {
            try {
                if (!resource.isFile()) {
                    continue;
                }
                String parentDir = resource.getFile().getParent();
                parentDirSet.add(Paths.get(parentDir));
            } catch (IOException e) {
                log.warn("getWatchMapperXmlFileDirPaths error resource={}", resource, e);
            }
        }
        return parentDirSet;
    }

    /**
     * 扫描xml文件所在的路径
     */
    private void scanMapperXml() {
        try {
            this.mapperLocations = new PathMatchingResourcePatternResolver().getResources(mybatisMapperFileLocationPattern);
        } catch (IOException e) {
            log.error("scanMapperXml error", e);
        }
    }


    @Override
    public void destroy() throws Exception {
        try {
            watcher.close();
        } catch (Exception e) {
            log.debug("DirectoryWatcher got an exception while close!", e);
        }
    }

    public void setMybatisMapperXmlFileReloadService(MybatisMapperXmlFileReloadService mybatisMapperXmlFileReloadService) {
        this.mybatisMapperXmlFileReloadService = mybatisMapperXmlFileReloadService;
    }

    /**
     * 启动监听 变更事件
     */
    private void runWatchService() {
        try {
            if (watcher != null) {
                watcher.watch();
            }
        } catch (Exception e) {
            log.debug("DirectoryWatcher got an exception while watching!", e);
        }
    }
}
