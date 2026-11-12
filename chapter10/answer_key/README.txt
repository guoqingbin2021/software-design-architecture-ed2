第10章习题参考代码

在answer_key目录使用JDK 17执行：
  javac -encoding UTF-8 --release 17 -d out Chapter10Answers.java
  java -cp out Chapter10Answers
显式检查失败会抛出AssertionError，无需-ea。out为本机生成目录。

10-8为时间窗口与节次纯函数；10-9独立SQL只读检查；10-10在临时工程副本执行原21项HTTP/MySQL测试和1项旧DELETE隔离测试。纯函数通过不替代数据库集成测试。

10-10：先按../course-selection/README.txt初始化专用course_test测试库，
设置TEST_DB_URL、TEST_DB_USER、TEST_DB_PASSWORD后执行：
  pwsh -File run-experiment.ps1 -MavenCommand mvn
TEST_DB_URL必须指向course_test；测试会重置其中业务数据。
可用-WorkDirectory指定全新目录保留构建结果，脚本不递归删除目录。
JUnit报告位于输出目录backend/target/surefire-reports。
读取invariants.sql并在本章专用库执行，只返回违规记录。

10-9测试矩阵（每项独立复位，核对HTTP响应及新事务中的最终值）：
初始条件 | 操作 | 预期
窗口[open,close) | 分别在open、close申请 | 前者允许，后者拒绝
已选9学分、上限12 | 分别增加3、4学分 | 12通过，13拒绝且不增加数据
班5关闭且王芳未评分 | 教师录入0、100、85.125 | 前两者通过，第三个拒绝
班5由teacher2任教 | teacher1查询名单或评分 | 403且成绩不变
学生已有成功键K | K+原班、K+其他班重试 | 返回原快照、409且无新占座
快照插入时注入异常 | 合法学生申请有名额班 | 人数、选课记录、请求快照共同回滚

各具体方法见../course-selection/docs/requirements-traceability.txt；
实际执行层次见../course-selection/docs/test-report.txt。
