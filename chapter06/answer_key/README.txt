第6章习题参考代码

在answer_key目录使用JDK 17执行：
  javac -encoding UTF-8 --release 17 -d out Chapter06Answers.java
  java -cp out Chapter06Answers
显式检查失败会抛出AssertionError，无需-ea。out为本机生成目录。

6-8验证命令撤销/重做与失败不改历史；6-9验证观察者快照及必需检查链；6-10核对合法请求保存一次、无权限保存及通知均为0。全部为单进程内存实验。
