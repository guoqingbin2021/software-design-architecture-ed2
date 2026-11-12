第4章习题参考代码

在answer_key目录使用JDK 17执行：
  javac -encoding UTF-8 --release 17 -d out Chapter04Answers.java
  java -cp out Chapter04Answers
显式检查失败会抛出AssertionError，无需-ea。out为本机生成目录。

4-8的Checkout.createPay是工厂方法，AliCheckout/WechatCheckout提供具体产品；factory仅用于简单工厂对比。4-9/4-10核对设备按值键复用与幂等关闭。设备和支付对象为教学替身。
