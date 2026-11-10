第5章 结构型设计模式：配套代码与材料

一、正文对应内容

通过导出功能讲解装饰、代理、授权、审计及结构组合。

二、目录说明

代码5-1：LegacyExportMain，保留raw拼接文本的旧导出反例。
代码5-2：DecoratorMain，脱敏、字段筛选、日志与包装顺序。
代码5-3：GzipMain，临时目录gzip写出与读回。
代码5-4：ControllerMain，Spring MockMvc展示未授权入口反例。
代码5-5：StaticProxyMain，静态代理的允许与拒绝。
代码5-6：DynamicProxyMain，JDK代理、异常与Object方法。
代码5-7：CglibMain，Spring CGLIB代理。
代码5-8：demo.MethodSecurityMain，方法授权注解的生效条件。
代码5-9：demo.MethodSecurityMain，身份、授权与审计。
代码5-10：demo.AuditMain，成功、拒绝与业务失败。
代码5-11：demo.BoundaryMain，自调用、协作服务、final/private边界。
代码5-12：demo.ExportMain，操作权限、门店范围与JSON/XML语义。
各目录均为独立Maven模块，不要混编同名类型。

三、运行环境

JDK 17、Maven 3.9。首次下载依赖和插件需要联网。代码5-8至5-11采用
Spring Framework 6.1.14与Spring Security 6.3.4，以pom.xml为准。
代码5-7使用org.springframework.cglib，无需另装CGLIB。

四、运行方法

在所选模块目录执行：
    mvn -q compile exec:java
也可在本章目录指定模块，例如：
    mvn -q -f "代码5-12/pom.xml" compile exec:java

五、验证方法

各入口显式检查失败会抛出AssertionError或使进程失败，无需-ea。
代码5-3完成后会打印临时文件位置。@EnableAspectJAutoProxy启用Spring代理，
不启用编译期或类加载期织入。所有身份和数据均为进程内教学数据，不连接
用户数据库。代码5-4及5-11绕过代理的路径为反例。方法授权控制操作资格，
门店范围通过服务与仓储落实，客户端不能扩大。各示例未实现持久化审计
或真实HTTP服务器。

六、习题答案

answer_key保存本章习题的代码或数据参考答案，运行方法见其中的运行说明.txt。
题目条件、分析过程和非代码答案见全书书末。答案与本章示例分开运行。
