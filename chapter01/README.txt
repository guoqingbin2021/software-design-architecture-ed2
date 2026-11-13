第1章 软件设计与体系结构概述：配套代码与材料

一、正文对应内容

打印设备替换、金额计算、成交快照与回归验证。

二、目录说明

PrintDemo.java、AmountDemo.java：可独立运行的完整示例。
src/main/java：按包组织的工程版本，其中counterexample保存旧结算反例。
src/test/java：JUnit 5测试。
CatalogSyncNotes.txt：商品版本、同步断点与故障恢复说明。

三、运行环境

JDK 17；运行工程测试还需Maven 3.9。

四、运行方法

在本章目录执行：
    javac -encoding UTF-8 --release 17 -d out PrintDemo.java AmountDemo.java
    java -ea -cp out PrintDemo
    java -ea -cp out AmountDemo

五、验证方法

在本章目录执行mvn test。独立示例必须加-ea，才能执行Java原生assert。
原单4900分、新价单5200分、满减单4400分。打印设备采用假对象，交易状态
保存在内存中；示例未接入数据库、支付平台或真实打印设备。

六、正文概览的补充说明

本章正文保留通知、取得数据与应用版本的区别；多级同步、断点及恢复细节
见CatalogSyncNotes.txt，第6章6.3节继续讨论观察者与发布订阅，第7章分析
部署及网络依赖。正文表1-11保留决定、代价与失效条件的简要记录。

AmountDemo用Math.multiplyExact、Math.addExact等方法检测long溢出。
满减门槛前后为3999分应付3999分、4000分应付3500分；完整程序还检查
零数量、负单价与溢出。比例优惠需单独约定舍入时点；本章固定减500分
不涉及分以下精度。原生assert必须使用-ea启用，仅出现PASS文字不足以
证明未启用的断言已经执行。

打印程序以内存状态和假设备说明职责，不实现数据库与外设的原子提交。
真实工程可持久化待打印任务并记录结果；在保存打印结果前崩溃时，
不能仅凭内存或旧状态认定是否出纸，应提供待核实状态并检查设备反馈。
