第6章 行为型设计模式：配套代码与材料

一、正文对应内容

以促销结算、通知、命令历史和数据同步说明行为型设计模式。

二、目录说明

TextbookCore.java：策略、观察者、命令历史与检查链。
Chapter6BodyReference.java：促销组合、失败重试、事件去重与通知异常。
Chapter6SupportingExamples.java：同步断点、增量完整性、撤销与重做、
取消明细及气象站等补充示例。
run-all.ps1：同时编译并运行三个程序。run-all.bat：同一脚本的桌面入口。

三、运行环境

JDK 17、Windows PowerShell；无需Maven和第三方库。

四、运行方法

在本章目录执行：
    powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File .\run-all.ps1
JDK不在PATH时附加-JdkHome "实际JDK17目录"。脚本在系统临时目录编译，
退出时只删除本次建立的临时编译目录。
也可把三个Java文件同时编译，再分别运行三个同名主类：
    javac --release 17 -encoding UTF-8 -d out TextbookCore.java Chapter6BodyReference.java Chapter6SupportingExamples.java
    java -Dfile.encoding=UTF-8 -cp out TextbookCore
    java -Dfile.encoding=UTF-8 -cp out Chapter6BodyReference
    java -Dfile.encoding=UTF-8 -cp out Chapter6SupportingExamples

五、验证方法

程序检查保存前失败、响应丢失后原请求重试、事件去重、通知异常、金额
与提交状态。金额以BigDecimal元表示，money归一为两位小数。给定400元
订单及促销顺序，折扣100应付370元、折扣90应付330元，不替换其他章节的
49元小计与44元优惠后金额。
业务数据为单进程、单线程内存状态；同步仅处理从1开始的连续版本日志
中的新增与修改，没有删除、稀疏游标、持久化事务、全量恢复或崩溃恢复。
通知异常不回滚已提交交易，去重记录不跨进程。输出的检查数是当前入口
检查项的数量，不是覆盖率。

六、阅读对应

同步断点、固定目标范围、分页完整性和失败恢复集中见正文6.3.3。
促销组合见6.6.3，四种模式分别负责计算、草稿编辑、提交检查与成功通知；
表6-13给出金额及状态预期，不将本程序的内存赋值视为持久化事务。
正文第6章参考文献[1]为Gamma等的Design Patterns（Addison-Wesley，1994），
[2]为Larman的Applying UML and Patterns第3版（Pearson Education，2005）。
