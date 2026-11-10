第九章 从选课请求到页面反馈：配套工程

一、工程与模型
backend为Spring Boot服务，frontend为Vue页面。Controller读取认证身份，
EnrollmentService安排事务，领域对象检查规则，Repository负责查询与加锁。
基础功能为学生选课、本人课表、退课和原请求结果查询。成绩、学分、时间
冲突和教师排课留到后续完整案例；score固定为null。

固定数据为101张敏（20260001）、102李明（20260002）、班501（SE301，
软件设计与体系结构，学期2026秋，容量30），其他29名学生已经有效选入。
101/102初始未选入。教师、地点、周次和时间未安排，返回null；第八章
假定教师已安排，本章增加未排课状态。同一学生与班永久唯一，退课后
再次选课沿用原id。所有实际id均从响应读取，不能假定为正文示例1001。

二、环境与启动
JDK 17、Maven 3.9、MySQL 8.0（CHECK要求>=8.0.16）、Node.js 22或24。
Spring Boot 3.3.5；前端精确依赖由package-lock.json固定。

使用Docker Desktop时，在项目根目录执行：
  Copy-Item .env.example .env
  pwsh -File scripts/start.ps1
启动完成后访问http://localhost:8081。页面、后端、数据库默认端口8081、
8080、3307，均绑定回环地址；.env可修改端口和专用教学数据库密码。
停止用scripts/stop.ps1或docker compose down，数据卷默认保留。

本机开发可只运行docker compose up -d mysql，或先建立专用enrollment_ch9
空库。配置SPRING_DATASOURCE_URL、SPRING_DATASOURCE_USERNAME、
SPRING_DATASOURCE_PASSWORD后，在backend执行mvn spring-boot:run。
frontend执行npm ci、npm run dev。Vite将/api代理到127.0.0.1:8080；
后端端口改变时同步修改frontend/vite.config.js。

教学账户student101、student102、teacher201的密码均为Chapter9!Demo。
teacher201用于观察学生接口的角色拒绝。页面凭据只保留内存，显式发送
Authorization并用credentials:'omit'排除Cookie；退出清除内存凭据。
服务器采用STATELESS HTTP Basic。远程访问须使用HTTPS；若改为Cookie
或浏览器自动附带认证信息，须重新配置CSRF防护。

三、公共契约
GET /api/me：当前身份；POST /api/enrollments：200原选课结果；
GET /api/enrollments/me：200本人固定学期的有效课表，无semester参数；
DELETE /api/enrollments/{id}：204，重复同一已退课记录仍为204；
GET /api/enrollment-requests/{key}：200原成功快照。404只表示当前尚未
看到完成快照，原事务可能仍在处理，不代表明确失败。

POST只接收数值teachingClassId。id与teachingClassId是1至9007199254740991
范围内的安全正整数；拒绝数字字符串、小数及未知字段。幂等键为1至80
个字母、数字、点、下划线或连字符；相同键只能对应同一原请求。
错误为{code,message,requestId,fields}，无字段错误时fields={}，requestId
与X-Request-Id相同。未认证401，角色或他人记录403，缺失/已删除404，
容量等业务冲突409。teacherName、room、week、time允许null。

四、响应丢失与恢复
POST超时、网络中断、5xx或协议错误后，保存原键与请求体并暂停新的写入。
查询原请求结果或使用原键原内容重试；历史成功快照证明过去已经完成，
显示当前业务状态仍要重新读课表。待确认信息按同一用户名暂存在浏览器，
不保存口令，刷新后须重新认证，不能将旧请求交给另一身份。

DELETE响应丢失后保存原记录id。点击“查询并恢复退课操作”，从无缓存
本人有效课表核对：原id消失则确认不再有效；仍在则重新确认同一DELETE；
网络失败或401继续保留不确定状态。不能通过新选课请求试探退课结果。

五、测试与数据恢复
scripts/test.ps1执行前端测试/构建及后端领域测试；加-Integration运行
Docker MySQL集成测试。没有Docker时先建立专用空测试库，设置：
  CH9_TEST_DB_URL（完整JDBC地址）
  CH9_TEST_DB_USER、CH9_TEST_DB_PASSWORD
然后在backend执行：mvn -Dit.test=LocalMySqlIT verify
集成测试会清空并重建业务数据，只能指向专用测试库，不能用开发或真实库。
测试断言覆盖认证、输入、幂等、越权、并发容量、事务回滚与关系复用；
前端包括丢失响应后恢复、身份切换与协议错误分支。

本次修订已执行领域测试、隔离MySQL集成测试、36项前端测试及生产构建。
JUnit报告由Maven生成，后续复核以本机实际输出为准。Docker容器启动流程
未作为本轮验证结论，真实MySQL验证使用独立临时实例。

重新演示时先停止后端，再连接专用enrollment_ch9库，执行
database/reset-fixture.sql。它删除操作和请求日志并恢复29条初始选课，
不会重置自增id，执行前核对当前库名。运行生成的.env、node_modules、
backend/target、frontend/dist与日志不属于发布源码。
