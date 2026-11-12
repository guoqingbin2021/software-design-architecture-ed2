第1章习题参考代码

在answer_key目录使用JDK 17执行：
  javac -encoding UTF-8 --release 17 -d out Chapter01Answers.java
  java -cp out Chapter01Answers
显式检查失败会抛出AssertionError，无需-ea。out为本机生成目录。

1-8核对满4000减500及溢出；1-9/1-10将同一Receipt交给两个Printer实现，核对id、4400分与输入不变。Checkout的去重与补打仅为内存选做示例，不提供跨重启恢复。
