第八章习题参考代码

8-8值对象（JDK 17）：
  javac -encoding UTF-8 --release 17 -d out Chapter08Answers.java
  java -cp out Chapter08Answers
核对0/100、85.0与85.00相等、拒绝85.125和越界；85.000可精确归一化。

8-6/8-9：queries.sql给出正文表的分页、总数与LEFT JOIN查询；
experiment.sql用会话临时表复现分页1003/1002、1001、空页及总数3。
在独立教学库的同一MySQL连接中执行整个experiment.sql即可。
queries.sql最后的审计UPDATE适用于下面实验的answer8_*独立表。

8-9/8-10真实MySQL实验：MySqlExperiment.java使用两个JDBC连接，
显式READ COMMITTED并观测performance_schema实际锁等待后才放行A，
分别验证A提交后B拒绝和A回滚后B成功。随后验证复用1001记录的恢复、
所属教师逻辑删除、ACTIVE拒绝、错误教师拒绝与重复删除保留原审计。

1. 用管理员在专用MySQL8教学实例建立answer8_test库。
   为实验账户授予该库权限及performance_schema的SELECT权限。
2. 获取JDBC驱动（Maven 3.9，首次需要网络）：
   mvn -q dependency:copy -Dartifact=com.mysql:mysql-connector-j:8.3.0 -DoutputDirectory=lib
3. 配置进程环境变量：
   ANSWER_DB_URL=jdbc:mysql://127.0.0.1:自己的端口/answer8_test?useSSL=false&serverTimezone=UTC
   ANSWER_DB_USER=自己的实验账户
   ANSWER_DB_PASSWORD=自己的实验密码
4. 执行：
   pwsh -File run-experiment.ps1 -DriverJar lib/mysql-connector-j-8.3.0.jar

脚本仅接受answer8_test。实验会清空并重建数据条件，只操作answer8_class和
answer8_enrollment两张表。数据库须为专门的实验库；不要连接正文业务库。
若performance_schema未启用或无读取权限，实验报错，不能据此报告并发通过。
完整PASS须同时看到COMMIT、ROLLBACK和HISTORY/AUDIT三项结果。
lib、out与本机数据库配置由运行时生成，不纳入源码。
