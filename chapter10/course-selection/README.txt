第十章 学生选课系统配套代码

一、案例范围

本工程对应《软件设计与体系结构》第十章正文中的学生选课案例。
学生可以查询教学班、选课、退课和查看个人课表；任课教师可以查询
本班学生名单，并为已经结束选课的教学班录入成绩。

候补、可靠通知、在线排课维护和服务拆分属于正文10.6节比较的扩展设计，
本工程不包含这些功能。班级人数、选课历史与请求快照保存在同一数据库中。

二、软件环境

JDK 17、Maven 3.9、MySQL 8.0、Node.js 22或24、PowerShell 7。
Spring Boot 3.3.5、Vue 3.5.13和Vite 5.4.21的版本已写入工程依赖文件。
前端使用package-lock.json固定依赖，.nvmrc记录Node.js版本。
首次安装依赖需要网络。将java、mvn、node、npm和mysql加入PATH。

默认后端端口为18080，前端端口为15173，MySQL示例端口为13316。
数据库应使用专用教学实例或独立的教学库，按本机端口修改.env中的地址。

三、建立数据库与启动工程

1. 在工程根目录打开PowerShell 7，执行：

   Copy-Item .env.example .env

   按本机情况修改.env中的数据库端口、用户名和密码。DB_URL用于开发库
   course_demo；TEST_DB_URL必须指向独立的course_test测试库。

2. 使用数据库管理员连接专用的教学MySQL实例：

   mysql --host=127.0.0.1 --port=13316 -u root -p --default-character-set=utf8mb4

   在MySQL客户端中执行：

   source scripts/init-database.sql
   exit

   脚本建立开发库、测试库和course_app账户。它不写入业务表；首次启动时
   由Flyway V1—V5建立表结构并写入教学数据。脚本中的数据库教学密码为
   Chapter10Db!，应与.env保持一致。测试触发器所需设置仅用于专用实验实例。

3. 在工程根目录检查前后端测试并完成构建：

   pwsh -File scripts/verify.ps1

   退出码为0表示执行成功。后端JUnit报告位于backend/target/surefire-reports；
   运行日志及总体结果写入evidence。测试会重置course_test中的业务数据，
   不应把TEST_DB_URL指向开发库。再次执行验证前先停止本工程的前后端服务。

4. 在第一个PowerShell终端启动后端：

   pwsh -File scripts/start-backend.ps1

5. 在第二个PowerShell终端启动前端：

   pwsh -File scripts/start-frontend.ps1

6. 打开浏览器，访问：http://127.0.0.1:15173

停止服务时，在相应终端按Ctrl+C。若mvn不在PATH中，可在.env加入
MAVEN_CMD=C:/tools/apache-maven-3.9.9/bin/mvn.cmd，并按本机路径修改。
若修改后端PORT，还要同步修改frontend/vite.config.js中的代理target。

四、教学账户与初始数据

所有页面教学账户的密码均为Chapter10!。

student1：张敏。初始个人课表为空，可完成例10-1。
student2：李明。可与张敏竞争班1的最后一个名额。
student3：王芳。初始已选入班5，成绩尚未录入。
teacher1：陈老师。可以查询班1、2、3、6及其学生名单。
teacher2：刘老师。可以查询班4、5，并为班5的王芳录入成绩。

班1：软件设计与体系结构，周一第1—2节，容量1，初始人数0。
班2：数据库原理，周一第2—3节，与班1时间冲突。
班3：Web应用开发，周二第1—2节，与班1时间不冲突。
班4：软件测试，周三第1—2节，由teacher2任课。
班5：计算机网络，周四第1—2节，CLOSED，初始人数1，可录入成绩。
班6：软件设计与体系结构，周五第1—2节，与班1属于同一课程。

这些账户仅用于本机教学。张敏、李明的主键从前章101/102改为1/2，学号
改为S2026001/S2026002；王芳为新增学生3（S2026003），不是前章王宁。
学期存储为2026-2027-1，显示2026秋，班501映射为本章容量1的班1。
本章按每周重复的星期及节次排课，不直接沿用第八章具体日期半开区间。
任课教师改为陈老师、刘老师，已排课，不包含第九章未安排的null情形。
正式系统需要采用学校的身份认证和HTTPS。

五、按正文阅读代码

10.1 系统分层：
  backend/src/main/java/edu/textbook/selection/api：接口、DTO与错误响应。
  security：读取认证身份与检查角色；service：安排业务处理步骤。
  domain：领域对象；repository：数据库查询与加锁。

10.2 数据库初始化与教学数据：
  backend/src/main/resources/db/migration/V1__schema.sql：表和约束。
  V2__teaching_seed.sql：学生、教师、课程与班1—5。
  V3__additional_rule_fixture.sql：同课程的班6。
  V4__closed_class_grading_fixture.sql：班5中未评分的王芳。
  V5__selection_window.sql：左闭右开的选退课窗口。

10.3 领域模型与并发控制：
  domain/Enrollment.java：有效记录、退课历史与成绩条件。
  domain/TeachingClass.java：名额与选课阶段。
  domain/MeetingSlot.java：星期和节次的重叠判断。
  repository/StudentRepository.java与TeachingClassRepository.java：悲观写锁。
  V1中的active_class_id与联合唯一键：只限制有效关系，保留退课历史。

10.4 业务功能与前后端集成：
  service/EnrollmentService.java：选课、退课、评分与同事务提交。
  代码10.1对应其中的enroll方法；全部步骤均保留在完整方法中。
  examples/example-10-1.http对应代码10.2中的选课请求与课表查询。
  frontend/src/App.vue：页面；store.js：提交状态与结果确认；api.js：请求。

10.5 系统验证：
  backend/src/test/java/edu/textbook/selection/CourseSelectionIntegrationTest.java：
  真实HTTP/MySQL集成测试，包括容量竞争、同学生冲突、旧键重放与故障回滚。
  frontend/tests：接口、查询乱序、退出、超时确认与页面规则测试。
  examples/example-10-3.sql：正文代码10.3的班1计数查询。
  scripts/verify-invariants.sql：操作结束后独立核对六项数据库不变量。
  docs/requirements-traceability.txt：功能、约束、代码与测试的对应关系。

六、完成例10-1

使用student1登录，确认个人课表为空、班1人数为0，再选择班1。成功后
班1人数为1，课表显示周一第1—2节。也可使用HTTP客户端执行
examples/example-10-1.http，发送的请求键为example-enroll-001。

重复发送同一请求键应返回原成功结果，不再占用名额。退课后使用原键
重试也不会恢复选课；需要重新选课时应使用新的请求键。选课记录编号
由数据库分配，退课与评分时使用服务器返回的id，不把教学班编号当作id。

teacher2登录后选择班5，应能看到王芳。输入85并保存，再查询名单，
成绩仍应为85。teacher1或学生直接访问班5名单时，服务器应拒绝访问。

七、恢复实验初始状态

重新启动应用不会清除已经保存的选退课与成绩。需要重新演示例题时，
先停止后端，在工程根目录使用course_app连接course_demo：

   mysql --host=127.0.0.1 --port=13316 -u course_app -p --default-character-set=utf8mb4 course_demo

输入与.env相同的数据库密码，再在MySQL客户端中执行：

   source scripts/reset-teaching-data.sql

该脚本会删除course_demo中的实验操作，重新写入本章的虚构教学数据；
执行前确认数据库为专门的教学库。完成后重新启动后端，张敏的课表为空，
班1人数为0，班5恢复王芳的未评分记录。Flyway迁移历史保持不变。

八、两项设计选择

1. 身份与事务

   学生编号由认证后的Actor提供，请求体只接收教学班编号。若允许页面
   自行提交学生编号，学生就可能替其他人选课。后端因此在每个写入入口
   检查角色和记录归属，教师操作还要检查任课关系。

   选课记录、班级人数与请求快照放在同一个事务中提交。这样，即使最后
   保存快照失败，前面的名额预留与选课记录也会回滚。若分别提交，可能
   出现人数已经增加而课表中没有记录，或选课已成功却无法识别重试。

2. 操作历史与请求重试

   退课把原记录改为WITHDRAWN，重新选课建立新记录，便于分别保存每次
   选退课。请求快照另存原成功响应，用学生编号与请求键识别原操作。
   学生退课后再发送旧键，服务器仍只返回原响应，不恢复原记录。

   因此，原成功响应只能证明过去的请求已处理；个人课表才能反映当前
   有效选课。前端收到成功或历史响应后重新查询课表，确认当前结果。

九、共同接口与恢复

GET /api/me返回认证身份；POST /api/enrollments返回200；
GET /api/enrollments/me返回本人固定单学期有效课表，不接受semester筛选；
DELETE /api/enrollments/{id}返回204，重复同一已退课id仍返回204；
GET /api/enrollment-requests/{key}返回本人原请求成功快照，404只表示尚未
看到完成记录，不能据此认定失败。成功快照与当前课表分别回答过去提交
是否完成和当前是否仍有效。超时后保留原键原请求体，先确认或原键重试。
退课响应丢失后读取本人有效课表，原id消失则解除待确认状态，仍在则
允许重试同一DELETE；读取失败继续等待。再次选课新建id，旧DELETE不
会取消新记录。

id、teachingClassId使用JSON安全正整数1至9007199254740991，拒绝数字
字符串与小数；幂等键为1至80个字母、数字、点、下划线或连字符。
错误体为{code,message,requestId,fields}，无字段错误fields={}，requestId
与X-Request-Id相同。401未认证，403角色/归属不允许，404缺失，409业务
冲突。页面凭据保留在内存，显式设置Authorization并排除Cookie；服务器
使用STATELESS HTTP Basic。改为Cookie或浏览器自动认证时重配CSRF。

本轮修订先复现原请求查询404问题，再通过隔离MySQL的21项HTTP集成
测试、36项前端测试与前后端构建；未把旧版测试报告当作当前结果。
本轮运行环境为JDK 17.0.9、Maven 3.9.9、MySQL 8.0.43、Node.js 24.22.1。
复核命令与输出位置见第三部分，运行时以当前实际结果为准。

十、文件使用

交付包包含源代码、依赖配置、SQL、启动与验证脚本、测试和例题请求。
运行与验证说明集中在本文件中，需求映射保留为纯文本。
运行时自行生成.env、node_modules、target、dist和测试日志，发布代码时
不携带本机.env、数据库目录及这些构建产物。
