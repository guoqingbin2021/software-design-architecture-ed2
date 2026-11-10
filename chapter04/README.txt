第4章 创建型设计模式：配套代码与材料

一、正文对应内容

通过支付渠道和打印设备的创建与装配，说明创建型设计模式。

二、目录说明

各目录为独立Maven模块，同名retail类型只在所属模块使用。
代码4-1：LegacyCheckout，固定ALI/SNBC直接创建的反例。
代码4-2：CheckoutMain，构造注入、替身、输入与失败顺序。
代码4-3：FactoryMethodMain，三渠道和两设备的六种组合。
代码4-4：DeploymentMain，本地与云端产品族及跨族拒绝。
代码4-5：RetailApp，Spring读取store.properties并装配结算。
补充4.2_单例模式：SingletonMain，五种单例获取路径。
补充4.2.3_设备池：DevicePoolMain，按门店、设备和型号复用。
补充4.3.1_简单工厂：SimpleFactoryMain，组合、未知标识与关闭。
补充4.5.1_反射创建：ReflectionMain，允许渠道与未登记渠道。
以上入口的包名均为retail。

三、运行环境

JDK 17、Maven 3.9。首次下载插件和Spring依赖需要联网。

四、运行方法

在所选模块目录执行：
    mvn -q compile exec:java
代码4-5从当前工作目录读取store.properties，须在该模块目录运行。
默认ALI/SNBC输出ALI:O001:4900；改为WECHAT并重启后输出WECHAT:O001:4900。

五、验证方法

各入口通过显式AssertionError报告失败，无需-ea。在代码4-5目录另执行：
    mvn -q compile exec:java "-Dexec.mainClass=retail.Checks"
检查结果对应本次内存场景。订单为O001、4900分，不计优惠和税费。
DigitalRmbPay与HpPrinter用于正文扩展分析，基线没有实现这两个类型。
调用者借用设备池中的设备，不逐次关闭；应用停止提交并等待任务结束后
再关闭池。MemoryPrinter同步记录模拟输出，不代替真实驱动的任务队列。

六、习题答案

answer_key保存本章习题的代码或数据参考答案，运行方法见其中的运行说明.txt。
题目条件、分析过程和非代码答案见全书书末。答案与本章示例分开运行。
