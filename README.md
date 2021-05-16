# mybatis-mapper-reload-spring-boot-start

[热更新 mybatis mapper xml 配合arthas-idea-plugin 插件执行热更新](https://www.yuque.com/wangji-yunque/ikhsmq/ctgcbg)


## 1、来源
arthas 可以支持热更新 mybaits 的mapper ？我认为是可以的，应该是可以支持的。一般来说可以调用spring context 就可以执行任意的spring bean的方法，只需要将mybaits 的热更新程序留个口子即可。因此来研究一下mybatis的热更新如何操作。

### 1.1 参考文章 

- [Mybatis实现*mapper.xml热部署-分子级更新](https://blog.csdn.net/chao_1990/article/details/85116284)
- [Mybatis Mapper配置XML文件热加载实现](https://www.dazhuanlan.com/2019/10/23/5daf59ef6251d/)
- [springboot mybatis xml热更新](https://blog.csdn.net/jht385/article/details/104037222)
- [https://github.com/search?q=mybatis++reload](https://github.com/search?q=mybatis++reload)
- [初探 SpringBoot DevTools 重启应用原理](https://blog.csdn.net/u013076044/article/details/90340899)
- [directory-watcher](https://github.com/gmethvin/directory-watcher)
### 1.2  原理

- 获取所有的mapper xml 文件
- 使用 [https://github.com/gmethvin/directory-watcher](https://github.com/gmethvin/directory-watcher) 进行文件dir 路径相关的监听。
- 手动mapper xml 变化进行 mybatis org.apache.ibatis.session.Configuration 相关变量清理加工，重新加载最新的一份。

这里直接使用 directory-watcher 封装后的文件监听、接口比较简单优雅、有兴趣的可以看看 spring boot devtools的文件监听 采用的自己实现定时轮训 [https://docs.spring.io/spring-boot/docs/current/reference/html/using-spring-boot.html#using-boot-devtools](https://docs.spring.io/spring-boot/docs/current/reference/html/using-spring-boot.html#using-boot-devtools)
org.springframework.boot.devtools.filewatch.FileSystemWatcher 
## 2、使用
### 2.1 配置
```
## 热更新是否启动 默认为false 
mybatis.mapper.reload.enable=true
## 需要进行监听的文件
mybatis.mapper.reload.mapper-location=classpath*:/**/*apper*.xml
```
```xml
<dependency>
  <groupId>com.github.WangJi92</groupId>
    <artifactId>mybatis-mapper-reload-sping-boot-start</artifactId>
    <version>0.0.3</version>
</dependency>
```
### 2.2 arthas 如何热更新

- 第一步获取到static spring context的 hashvalue
```bash
hashValue=`$HOME/opt/arthas/as.sh  --select com.wangji92.arthas.plugin.demo.ArthasPluginDemoApplication -c 'sc -d com.wangji92.arthas.plugin.demo.common.ApplicationContextProvider' | awk '/classLoaderHash/{print $2;}' | head -1
```

- 上传mapper xml 到服务器指定目录
- 调用 com.github.wangji92.mybatis.reload.core.MybatisMapperXmlFileReloadService#reloadAllSqlSessionFactoryMapper 执行mapper 热更新
```bash
$HOME/opt/arthas/as.sh  --select com.wangji92.arthas.plugin.demo.ArthasPluginDemoApplication -c "#springContext=@com.wangji92.arthas.plugin.demo.common.ApplicationContextProvider@context,#springContext.getBean("mybatisMapperXmlFileReloadService").reloadAllSqlSessionFactoryMapper("/root/xxxmapper.xml")' -c $hashValue " | tee text.txt
```
结合上面的shell 脚本 基本上可以流程化处理服务器 mapper xml的热更新。


### mapper reload 日志
command+F9 热更新xml 查询名称 从* 变为 age的 过程
[https://github.com/WangJi92/mybatis-log-demo](https://github.com/WangJi92/mybatis-log-demo)

```
2021-05-10 22:58:00.048 DEBUG 1190 --- [nio-7012-exec-1] o.s.web.servlet.DispatcherServlet        : GET "/user/findAll", parameters={}
2021-05-10 22:58:00.058 DEBUG 1190 --- [nio-7012-exec-1] s.w.s.m.m.a.RequestMappingHandlerMapping : Mapped to public java.util.List<com.boot.mybatis.mybatisdemo.model.dataobject.UserDo> com.boot.mybatis.mybatisdemo.controller.UserController.findAll()
2021-05-10 22:58:00.134  INFO 1190 --- [nio-7012-exec-1] s.b.a.MybatisSqlCompletePrintInterceptor : SQL:select * from user WHERE ( name is not null )    执行耗时=9
2021-05-10 22:58:00.147 DEBUG 1190 --- [nio-7012-exec-1] m.m.a.RequestResponseBodyMethodProcessor : Using 'application/json', given [*/*] and supported [application/json, application/*+json, application/json, application/*+json]
2021-05-10 22:58:00.148 DEBUG 1190 --- [nio-7012-exec-1] m.m.a.RequestResponseBodyMethodProcessor : Writing [[UserDo [Hash = 65438785, name=汪吉, age=27, type=worker], UserDo [Hash = -1080850091, name=wangji1, a (truncated)...]
2021-05-10 22:58:00.167 DEBUG 1190 --- [nio-7012-exec-1] o.s.web.servlet.DispatcherServlet        : Completed 200 OK
2021-05-10 22:58:13.686  INFO 1190 --- [pper xml reload] .m.r.c.MybatisMapperXmlFileReloadService : reload new mapper file success path=/Users/wangji/Documents/project/mybatis-demo/target/classes/com/boot/mybatis/mybatisdemo/mapper/UserDoMapper.xml
2021-05-10 22:58:13.686  INFO 1190 --- [pper xml reload] w.m.r.c.MybatisMapperXmlFileWatchService : reload all count =/Users/wangji/Documents/project/mybatis-demo/target/classes/com/boot/mybatis/mybatisdemo/mapper/UserDoMapper.xml current result=2 mapper path=true 
2021-05-10 22:58:16.357 DEBUG 1190 --- [nio-7012-exec-3] o.s.web.servlet.DispatcherServlet        : GET "/user/findAll", parameters={}
2021-05-10 22:58:16.361 DEBUG 1190 --- [nio-7012-exec-3] s.w.s.m.m.a.RequestMappingHandlerMapping : Mapped to public java.util.List<com.boot.mybatis.mybatisdemo.model.dataobject.UserDo> com.boot.mybatis.mybatisdemo.controller.UserController.findAll()
2021-05-10 22:58:16.367  INFO 1190 --- [nio-7012-exec-3] s.b.a.MybatisSqlCompletePrintInterceptor : SQL:select age from user WHERE ( name is not null )    执行耗时=4
2021-05-10 22:58:16.368 DEBUG 1190 --- [nio-7012-exec-3] m.m.a.RequestResponseBodyMethodProcessor : Using 'application/json', given [*/*] and supported [application/json, application/*+json, application/json, application/*+json]
2021-05-10 22:58:16.368 DEBUG 1190 --- [nio-7012-exec-3] m.m.a.RequestResponseBodyMethodProcessor : Writing [[UserDo [Hash = 30628, name=null, age=27, type=null], UserDo [Hash = 29791, name=null, age=0, type=n (truncated)...]
2021-05-10 22:58:16.369 DEBUG 1190 --- [nio-7012-exec-3] o.s.web.servlet.DispatcherServlet        : Completed 200 OK

```
