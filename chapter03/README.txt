第3章 面向对象设计原则：配套代码与材料

一、正文对应内容

对应正文3.1—3.8：计价、账户职责、支付抽象、对象组合、接口能力、
替换契约与授权DTO。

二、目录说明

Chapter3Examples.java：包含完整类型、演示入口与八组固定输入验证。

三、运行环境

JDK 17，无第三方依赖。

四、运行方法

在本章目录执行：
    javac -encoding UTF-8 --release 17 -d out Chapter3Examples.java
    java -cp out Chapter3Examples

五、验证方法

执行：
    java -cp out Chapter3Examples --test
验证失败会抛出AssertionError并以非0退出，无需-ea。检查包括五类会员、
生日优惠逐步舍入、防溢出、支付审批与拒绝、响应丢失、重复结果、付款
核对和本地处理恢复。HundredOnlyAccount是违反契约的反例，验证应检出它。
程序使用单进程、单线程与内存状态；支付均为本地模拟。一个订单只创建
一次付款尝试，未知结果查询原attemptId。多次尝试、持久化、并发、权限
来源和验签需在实际系统中另行实现。优惠按声明顺序逐步按分舍入，不能
合并为一次乘法；金额以分表示，积分为整数。

六、习题答案

answer_key保存本章习题的代码或数据参考答案，运行方法见其中的README.txt。
题目条件、分析过程和非代码答案见全书书末。答案与本章示例分开运行。
