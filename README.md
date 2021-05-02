# mybatis-mapper-reload-spring-boot-start
热更新 mybatis mapper xml

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
    <version>0.0.2</version>
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
### 2.3 实践 watch 获取spring context 进行热更新mapper
执行arthas watch 
#### 2.3.1 执行arthas的脚本
```bash
watch -x 3 -n 1  org.springframework.web.servlet.DispatcherServlet doDispatch '@org.springframework.web.context.support.WebApplicationContextUtils@getWebApplicationContext(params[0].getServletContext()).getBean("mybatisMapperXmlFileReloadService").reloadAllSqlSessionFactoryMapper("/Users/wangji/Documents/project/mybatis-demo/src/main/resources/com/boot/mybatis/mybatisdemo/mapper/UserDoMapperExt.xml")'
```
#### 2.3.2 错误的文件
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
44<mapper namespace="com.boot.mybatis.mybatisdemo.mapper.UserDoMapper">

</mapper>
```
#### 2.3.3 执行结果
##### 2.3.3.1 arthas 
```bash
[arthas@85871]$ watch -x 3 -n 1  org.springframework.web.servlet.DispatcherServlet doDispatch '@org.springframework.web.context.support.WebApplicationContextUtils@getWebApplicationContext(params[0].getServletContext()).getBean("mybatisMapperXmlFileReloadService").reloadAllSqlSessionFactoryMapper("/Users/wangji/Documents/project/mybatis-demo/src/main/resources/com/boot/mybatis/mybatisdemo/mapper/UserDoMapperExt.xml")'
Press Q or Ctrl+C to abort.
Affect(class count: 1 , method count: 1) cost in 99 ms, listenerId: 2
method=org.springframework.web.servlet.DispatcherServlet.doDispatch location=AtExit
ts=2021-05-02 15:16:57; [cost=18.735604ms] result=@Boolean[false]
Command execution times exceed limit: 1, so command will exit. You can set it with -n option.
[arthas@85871]$ 

```
##### 2.3.3.2 应用日志
```
2021-05-02 15:16:57.286  WARN 85871 --- [nio-7012-exec-7] .m.r.c.MybatisMapperXmlFileReloadService : load fail /Users/wangji/Documents/project/mybatis-demo/src/main/resources/com/boot/mybatis/mybatisdemo/mapper/UserDoMapperExt.xml

org.apache.ibatis.builder.BuilderException: Error creating document instance.  Cause: org.xml.sax.SAXParseException; lineNumber: 3; columnNumber: 1; 前言中不允许有内容。
	at org.apache.ibatis.parsing.XPathParser.createDocument(XPathParser.java:260) ~[mybatis-3.5.2.jar:3.5.2]
	at org.apache.ibatis.parsing.XPathParser.<init>(XPathParser.java:86) ~[mybatis-3.5.2.jar:3.5.2]
	at com.github.wangji92.mybatis.reload.core.MybatisMapperXmlFileReloadService.removeMapperCacheAndReloadNewMapperFile(MybatisMapperXmlFileReloadService.java:88) [mmybatis-mapper-reload-sping-boot-start-0.0.1-SNAPSHOT.jar:0.0.1-SNAPSHOT]
	at com.github.wangji92.mybatis.reload.core.MybatisMapperXmlFileReloadService.lambda$reloadAllSqlSessionFactoryMapper$0(MybatisMapperXmlFileReloadService.java:64) [mmybatis-mapper-reload-sping-boot-start-0.0.1-SNAPSHOT.jar:0.0.1-SNAPSHOT]
	at java.util.stream.ForEachOps$ForEachOp$OfRef.accept(ForEachOps.java:184) ~[na:1.8.0_181]
	at java.util.ArrayList$ArrayListSpliterator.forEachRemaining(ArrayList.java:1382) ~[na:1.8.0_181]
	at java.util.stream.AbstractPipeline.copyInto(AbstractPipeline.java:481) ~[na:1.8.0_181]
	at java.util.stream.ForEachOps$ForEachTask.compute(ForEachOps.java:291) ~[na:1.8.0_181]
	at java.util.concurrent.CountedCompleter.exec(CountedCompleter.java:731) ~[na:1.8.0_181]
	at java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:289) ~[na:1.8.0_181]
	at java.util.concurrent.ForkJoinTask.doInvoke(ForkJoinTask.java:401) ~[na:1.8.0_181]
	at java.util.concurrent.ForkJoinTask.invoke(ForkJoinTask.java:734) ~[na:1.8.0_181]
	at java.util.stream.ForEachOps$ForEachOp.evaluateParallel(ForEachOps.java:160) ~[na:1.8.0_181]
	at java.util.stream.ForEachOps$ForEachOp$OfRef.evaluateParallel(ForEachOps.java:174) ~[na:1.8.0_181]
	at java.util.stream.AbstractPipeline.evaluate(AbstractPipeline.java:233) ~[na:1.8.0_181]
	at java.util.stream.ReferencePipeline.forEach(ReferencePipeline.java:418) ~[na:1.8.0_181]
	at java.util.stream.ReferencePipeline$Head.forEach(ReferencePipeline.java:583) ~[na:1.8.0_181]
	at com.github.wangji92.mybatis.reload.core.MybatisMapperXmlFileReloadService.reloadAllSqlSessionFactoryMapper(MybatisMapperXmlFileReloadService.java:62) [mmybatis-mapper-reload-sping-boot-start-0.0.1-SNAPSHOT.jar:0.0.1-SNAPSHOT]
	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method) ~[na:1.8.0_181]
	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62) ~[na:1.8.0_181]
	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43) ~[na:1.8.0_181]
	at java.lang.reflect.Method.invoke(Method.java:498) ~[na:1.8.0_181]
	at ognl.OgnlRuntime.invokeMethod(OgnlRuntime.java:899) ~[na:na]
	at ognl.OgnlRuntime.callAppropriateMethod(OgnlRuntime.java:1544) ~[na:na]
	at ognl.ObjectMethodAccessor.callMethod(ObjectMethodAccessor.java:68) ~[na:na]
	at ognl.OgnlRuntime.callMethod(OgnlRuntime.java:1620) ~[na:na]
	at ognl.ASTMethod.getValueBody(ASTMethod.java:91) ~[na:na]
	at ognl.SimpleNode.evaluateGetValueBody(SimpleNode.java:212) ~[na:na]
	at ognl.SimpleNode.getValue(SimpleNode.java:258) ~[na:na]
	at ognl.ASTChain.getValueBody(ASTChain.java:141) ~[na:na]
	at ognl.SimpleNode.evaluateGetValueBody(SimpleNode.java:212) ~[na:na]
	at ognl.SimpleNode.getValue(SimpleNode.java:258) ~[na:na]
	at ognl.Ognl.getValue(Ognl.java:470) ~[na:na]
	at ognl.Ognl.getValue(Ognl.java:572) ~[na:na]
	at ognl.Ognl.getValue(Ognl.java:542) ~[na:na]
	at com.taobao.arthas.core.command.express.OgnlExpress.get(OgnlExpress.java:37) ~[na:na]
	at com.taobao.arthas.core.advisor.AdviceListenerAdapter.getExpressionResult(AdviceListenerAdapter.java:123) ~[na:na]
	at com.taobao.arthas.core.command.monitor200.WatchAdviceListener.watching(WatchAdviceListener.java:86) ~[na:na]
	at com.taobao.arthas.core.command.monitor200.WatchAdviceListener.finishing(WatchAdviceListener.java:70) ~[na:na]
	at com.taobao.arthas.core.command.monitor200.WatchAdviceListener.afterReturning(WatchAdviceListener.java:54) ~[na:na]
	at com.taobao.arthas.core.advisor.AdviceListenerAdapter.afterReturning(AdviceListenerAdapter.java:57) ~[na:na]
	at com.taobao.arthas.core.advisor.SpyImpl.atExit(SpyImpl.java:67) ~[na:na]
	at java.arthas.SpyAPI.atExit(SpyAPI.java:64) ~[na:3.5.0]
	at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:32) ~[spring-webmvc-5.1.9.RELEASE.jar:5.1.9.RELEASE]
	at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:942) ~[spring-webmvc-5.1.9.RELEASE.jar:5.1.9.RELEASE]
	at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1005) ~[spring-webmvc-5.1.9.RELEASE.jar:5.1.9.RELEASE]
	at org.springframework.web.servlet.FrameworkServlet.doGet(FrameworkServlet.java:897) ~[spring-webmvc-5.1.9.RELEASE.jar:5.1.9.RELEASE]
	at javax.servlet.http.HttpServlet.service(HttpServlet.java:634) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:882) ~[spring-webmvc-5.1.9.RELEASE.jar:5.1.9.RELEASE]
	at javax.servlet.http.HttpServlet.service(HttpServlet.java:741) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:231) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:53) ~[tomcat-embed-websocket-9.0.22.jar:9.0.22]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:193) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.springframework.boot.actuate.web.trace.servlet.HttpTraceFilter.doFilterInternal(HttpTraceFilter.java:88) ~[spring-boot-actuator-2.1.7.RELEASE.jar:2.1.7.RELEASE]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:118) ~[spring-web-5.1.9.RELEASE.jar:5.1.9.RELEASE]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:193) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.springframework.web.filter.RequestContextFilter.doFilterInternal(RequestContextFilter.java:99) ~[spring-web-5.1.9.RELEASE.jar:5.1.9.RELEASE]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:118) ~[spring-web-5.1.9.RELEASE.jar:5.1.9.RELEASE]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:193) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.springframework.web.filter.FormContentFilter.doFilterInternal(FormContentFilter.java:92) ~[spring-web-5.1.9.RELEASE.jar:5.1.9.RELEASE]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:118) ~[spring-web-5.1.9.RELEASE.jar:5.1.9.RELEASE]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:193) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.springframework.web.filter.HiddenHttpMethodFilter.doFilterInternal(HiddenHttpMethodFilter.java:93) ~[spring-web-5.1.9.RELEASE.jar:5.1.9.RELEASE]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:118) ~[spring-web-5.1.9.RELEASE.jar:5.1.9.RELEASE]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:193) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.springframework.boot.actuate.metrics.web.servlet.WebMvcMetricsFilter.filterAndRecordMetrics(WebMvcMetricsFilter.java:114) ~[spring-boot-actuator-2.1.7.RELEASE.jar:2.1.7.RELEASE]
	at org.springframework.boot.actuate.metrics.web.servlet.WebMvcMetricsFilter.doFilterInternal(WebMvcMetricsFilter.java:104) ~[spring-boot-actuator-2.1.7.RELEASE.jar:2.1.7.RELEASE]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:118) ~[spring-web-5.1.9.RELEASE.jar:5.1.9.RELEASE]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:193) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:200) ~[spring-web-5.1.9.RELEASE.jar:5.1.9.RELEASE]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:118) ~[spring-web-5.1.9.RELEASE.jar:5.1.9.RELEASE]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:193) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:202) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:96) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.apache.catalina.authenticator.AuthenticatorBase.invoke(AuthenticatorBase.java:490) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:139) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:92) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:74) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:343) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.apache.coyote.http11.Http11Processor.service(Http11Processor.java:408) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.apache.coyote.AbstractProcessorLight.process(AbstractProcessorLight.java:66) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.apache.coyote.AbstractProtocol$ConnectionHandler.process(AbstractProtocol.java:853) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:1587) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:49) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149) ~[na:1.8.0_181]
	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624) ~[na:1.8.0_181]
	at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:61) ~[tomcat-embed-core-9.0.22.jar:9.0.22]
	at java.lang.Thread.run(Thread.java:748) ~[na:1.8.0_181]
Caused by: org.xml.sax.SAXParseException: 前言中不允许有内容。
	at com.sun.org.apache.xerces.internal.util.ErrorHandlerWrapper.createSAXParseException(ErrorHandlerWrapper.java:203) ~[na:1.8.0_181]
	at com.sun.org.apache.xerces.internal.util.ErrorHandlerWrapper.fatalError(ErrorHandlerWrapper.java:177) ~[na:1.8.0_181]
	at com.sun.org.apache.xerces.internal.impl.XMLErrorReporter.reportError(XMLErrorReporter.java:400) ~[na:1.8.0_181]
	at com.sun.org.apache.xerces.internal.impl.XMLErrorReporter.reportError(XMLErrorReporter.java:327) ~[na:1.8.0_181]
	at com.sun.org.apache.xerces.internal.impl.XMLScanner.reportFatalError(XMLScanner.java:1472) ~[na:1.8.0_181]
	at com.sun.org.apache.xerces.internal.impl.XMLDocumentScannerImpl$PrologDriver.next(XMLDocumentScannerImpl.java:994) ~[na:1.8.0_181]
	at com.sun.org.apache.xerces.internal.impl.XMLDocumentScannerImpl.next(XMLDocumentScannerImpl.java:602) ~[na:1.8.0_181]
	at com.sun.org.apache.xerces.internal.impl.XMLDocumentFragmentScannerImpl.scanDocument(XMLDocumentFragmentScannerImpl.java:505) ~[na:1.8.0_181]
	at com.sun.org.apache.xerces.internal.parsers.XML11Configuration.parse(XML11Configuration.java:842) ~[na:1.8.0_181]
	at com.sun.org.apache.xerces.internal.parsers.XML11Configuration.parse(XML11Configuration.java:771) ~[na:1.8.0_181]
	at com.sun.org.apache.xerces.internal.parsers.XMLParser.parse(XMLParser.java:141) ~[na:1.8.0_181]
	at com.sun.org.apache.xerces.internal.parsers.DOMParser.parse(DOMParser.java:243) ~[na:1.8.0_181]
	at com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderImpl.parse(DocumentBuilderImpl.java:339) ~[na:1.8.0_181]
	at org.apache.ibatis.parsing.XPathParser.createDocument(XPathParser.java:258) ~[mybatis-3.5.2.jar:3.5.2]
	... 95 common frames omitted
```
