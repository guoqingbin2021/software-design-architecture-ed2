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

六、习题答案

answer_key保存本章习题的代码或数据参考答案，运行方法见其中的运行说明.txt。
题目条件、分析过程和非代码答案见全书书末。答案与本章示例分开运行。
