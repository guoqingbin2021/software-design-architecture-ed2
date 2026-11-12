第2章习题参考代码

在answer_key目录使用JDK 17执行：
  javac -encoding UTF-8 --release 17 -d out Chapter02Answers.java
  java -cp out Chapter02Answers
显式检查失败会抛出AssertionError，无需-ea。out为本机生成目录。

2-8分别由Line、Sale与TaxCalculator承担小计、汇总与含税税额。4400分由题设外部活动给定，Sale只汇总明细；独立会员活动采用1/.98/.96/.95，不叠加满减。2-9/2-10用固定回执序列验证订单、attempt、金额与终态，未连接支付平台。
