第十章 学生选课系统配套代码

一、运行环境
JDK 17、Maven 3.9、MySQL 8.0、Node.js 22或24、PowerShell 7。
依赖版本见backend/pom.xml、frontend/package.json和package-lock.json。
默认端口：MySQL 13316、后端18080、前端15173。

二、初始化与启动
1. 在course-selection目录执行：
   Copy-Item .env.example .env
   修改数据库连接和账户。DB_URL使用course_demo，TEST_DB_URL使用独立的
   course_test。将java、mvn、node、npm和mysql加入PATH；Maven也可通过
   .env中的MAVEN_CMD指定完整路径。
2. 用管理员账户连接专用教学MySQL实例：
   mysql --host=127.0.0.1 --port=13316 -u root -p --default-character-set=utf8mb4
   在MySQL客户端执行：
   source scripts/init-database.sql
   exit
   脚本建立两个空库和course_app教学账户，示例数据库密码为Chapter10Db!。
   首次启动由Flyway V1—V5建立表和教学数据。
3. 在工程根目录验证：
   pwsh -File scripts/verify.ps1
   测试会重置course_test中的业务数据。验证前停止本目录正在运行的服务。
4. 在两个终端分别启动：
   pwsh -File scripts/start-backend.ps1
   pwsh -File scripts/start-frontend.ps1
   浏览器打开http://127.0.0.1:15173。停止时在对应终端按Ctrl+C。
   修改后端PORT时，同步修改frontend/vite.config.js中的代理地址。

三、教学账户与数据
页面账户密码均为Chapter10!。
student1：张敏，初始课表为空；student2：李明，可竞争班1的最后名额。
student3：王芳，已选班5，尚未评分。
teacher1：陈老师，任教班1、2、3、6；teacher2：刘老师，任教班4、5。

班1：软件设计与体系结构，周一第1—2节，容量1，人数0。
班2：数据库原理，周一第2—3节，与班1冲突。
班3：Web应用开发，周二第1—2节。
班4：软件测试，周三第1—2节。
班5：计算机网络，周四第1—2节，CLOSED，王芳已选入。
班6：软件设计与体系结构，周五第1—2节，与班1属于同一课程。
本章采用固定单学期2026-2027-1（页面显示2026秋）的独立教学数据，
按每周重复的星期及节次排课；各编号以本工程数据库和接口返回值为准。

四、阅读顺序
backend/src/main/java/edu/textbook/selection/：
  api：HTTP接口、DTO和统一错误响应；security：身份与角色；
  domain：领域对象；service/EnrollmentService.java：业务流程与事务；
  repository：查询与悲观写锁。
backend/src/main/resources/db/migration/：V1为表与约束，V2—V4为教学数据，
  V5为选退课时间窗口。
frontend/src/：App.vue为页面，store.js为查询及提交状态，api.js为请求协议。
examples/example-10-1.http：选课请求与课表查询；example-10-3.sql：计数查询。
docs/requirements-traceability.txt：需求、实现和测试的对应关系。
docs/test-report.txt：本次修订实际执行的验证结果。

五、完成课堂演示
student1登录后选择班1。人数应变为1，个人课表显示周一第1—2节。
重复发送同一请求键返回原结果，不再次占座；退课后重放原键也不恢复选课。
重新选课使用新键并创建新记录，退课与评分使用接口返回的选课记录id。
teacher2选择班5，为王芳保存85分，再查询名单确认；其他教师不能访问本班。

六、核心规则
六项功能为教学班查询、选课、退课、个人课表、本班学生名单和成绩录入。
同学期检查重复课程、时间冲突与学分上限；容量、选退课窗口和成绩状态由
服务器再次检查。身份由认证账户决定，教师操作检查任课关系。

写操作依次锁学生和教学班。学生锁保护个人课表及学分检查，教学班锁保护
共享名额。班级人数、选课记录和请求成功快照在同一事务中提交或回滚。
退课保留WITHDRAWN历史，重新选课创建新id；迟到的旧DELETE仍只定位旧行。
数据库生成列与联合唯一键只限制有效关系，允许保留多轮历史。

选课结果未知时保留原请求键与教学班编号，先查询请求快照及当前个人课表，
再决定是否用原键重试。快照404表示尚未看到完成记录，不能据此判定失败。
旧成功快照证明原请求曾经完成，当前是否已选仍以有效课表为准。
退课或评分响应丢失时，分别重新查询课表或名单，再决定是否重复原操作。

接口公共id采用JSON安全正整数；幂等键为1—80个字母、数字、点、下划线或
连字符。错误响应包含code、message、requestId、fields。
教学环境使用HTTP Basic，页面凭据仅保存在内存；正式部署需使用HTTPS。

七、验证与重置
verify.ps1执行真实MySQL后端集成测试、前端测试与前后端构建。
后端报告在backend/target/surefire-reports，过程日志在evidence/pipeline-*.log，
总体结果在evidence/pipeline-result.txt。每次修改后应重新执行验证。
操作后可运行scripts/verify-invariants.sql，六条检查应均无违规记录。

恢复开发库初始数据：先停止后端，用course_app连接course_demo，执行：
   source scripts/reset-teaching-data.sql
该操作清除教学操作并恢复初始数据，Flyway迁移历史保持不变。

交付源代码、依赖、迁移、测试与脚本；.env、node_modules、target、dist、
数据库目录和本机日志在运行时生成，不纳入代码发布。
