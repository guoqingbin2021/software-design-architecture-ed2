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

六、配置与反射创建的诊断

补充4.5.1_反射创建使用允许名单把渠道映射到随应用发布的类，配置不接受
任意类名。先检查配置文件与必需项，再核对渠道、接口和构造条件。常见
异常按发生位置区分：
  配置文件不存在或读取失败：IOException及其子类，由读取入口报告。
  渠道缺失或未登记：IllegalArgumentException，在加载实现类前拒绝。
  找不到实现类：ClassNotFoundException。
  实现类不满足Payment接口：ClassCastException，来自asSubclass。
  找不到约定的公有构造方法：NoSuchMethodException。
  抽象类等不能实例化的类型：InstantiationException。
  构造方法访问受限：IllegalAccessException；访问检查也可能产生
    SecurityException，应结合运行环境与模块配置诊断。
  构造方法内部抛错：InvocationTargetException，用getCause查看原原因。
  类初始化或链接失败：可传播ExceptionInInitializerError、
    NoClassDefFoundError等Error，不能只按受检反射异常处理。
工厂把ClassCastException与ReflectiveOperationException包装为携带渠道
标识的IllegalStateException，并保留原因；其余错误不因此变为成功结果。
允许名单和接口检查都不验证业务实现正确性，仍须核对金额等契约。

代码4-5的RetailApp先显式加载store.properties，将属性源加入Environment，
再注册配置并refresh；不能仅凭文件名认为Spring已经读取配置。启动前检查
必需项、重复定义及类型转换，源码和配置按同一版本发布。延迟创建仍可能
在首次访问时失败。修改配置须重启或显式重建，热切换还要安排在途任务及
旧资源释放。

七、设备池的生命周期检查

设备池按门店、设备和型号组成身份键，同键复用，不同键分别创建。获取
同步只保护缓存与创建；MemoryPrinter同步记录输出，真实驱动仍需自己的
打印协调。借用者不逐次关闭设备。会话结束时停止新任务，等待在途任务
完成，再由拥有者关闭池。池关闭后拒绝获取；模拟驱动重复close仅释放一次，
真实关闭故障须另外处理。运行期替换设备也须明确新旧驱动切换及释放时机。
可使用8线程、64次同键获取，核对引用一致与构造1次，并检查重复关闭、
关闭后拒绝、不同池互不干扰。有限并发运行不能替代同步与内存模型分析。
