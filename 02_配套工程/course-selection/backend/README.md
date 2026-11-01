# 选课后端

> 历史证据位置：本文件记载的evidence路径指原始测试记录.zip内部的相对路径，统一见配套资源包的04_测试与验收。重新执行自检时，当前结果写入工程根目录evidence。

教学基线：JDK 17、Spring Boot 3.3.5、Maven 3.9、MySQL 8.0.16+ / InnoDB。Flyway 管理建表和固定种子，Hibernate 仅验证表结构。

## 运行

先创建两个独立空库 `course_demo` 和 `course_test`（字符集 `utf8mb4`），为教学用户授权。本次隔离实例端口为 13316；已有实例可通过环境变量修改。测试会清空测试库业务数据，请始终指向专用测试库。

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:13316/course_demo?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
$env:DB_USER='course_app'
$env:DB_PASSWORD='Chapter10Db!'
mvn clean package -DskipTests
java -jar target/course-selection-1.0.0.jar
```

`PORT` 默认为 `18080`。`DB_PASSWORD` 必须显式提供，遗漏时应用启动失败。浏览器页面通过 Vite 的 `/api` 代理访问后端；不要将这组固定教学密码部署到公网。

## 接口契约

所有接口使用 HTTP Basic。固定账户 `student1`、`student2`、`student3`、`teacher1`、`teacher2`，密码均为 `Chapter10!`；数据库保存 BCrypt 摘要。学生和教师的 personId 分别为 1/2/3 与 1/2，角色为 `STUDENT` / `TEACHER`。请求中的未知字段会被拒绝，学生身份只取认证结果。

| 方法 | 路径 | 输入与结果 |
|---|---|---|
| GET | `/api/me` | `{username,role,personId,name}` |
| GET | `/api/teaching-classes` | 学生查全部、教师仅查本人；数组 `{id,courseCode,courseName,teacherName,capacity,enrolledCount,dayOfWeek,startPeriod,endPeriod,credits,status}` |
| POST | `/api/enrollments` | `{teachingClassId:1}`，必填 `Idempotency-Key`；200 `{id,teachingClassId,courseName,status,score}` |
| GET | `/api/enrollments/me` | 本人 ACTIVE 课表数组，含选课 id 和展开的教学班、课程、教师、时间及学分字段 |
| DELETE | `/api/enrollments/{id}` | 本人选课退课；204，无响应体；重复退课仍为 204 |
| GET | `/api/teaching-classes/{id}/roster` | 任课教师的 ACTIVE 名单 `[{id,studentId,studentName,status,score}]`；id 为 enrollmentId |
| PUT | `/api/enrollments/{id}/score` | 任课教师提交 `{score:85}`；200 更新后的选课对象 |

错误统一为 `{code,message,requestId}`，同时返回 `X-Request-Id`。401 未认证；403 角色或归属不符；404 对象不存在；409 业务冲突；400 输入校验；405 方法不支持（保留 Allow）；415 媒体类型不支持；500 服务故障。成绩允许 0—100、最多两位小数，null 表示未评分。成绩只允许在选课阶段结束（班级 CLOSED）后录入。当前教学样例只处理一个固定学期，所有教学班均用 MeetingSlot 值对象表示每周一个固定连续时段。

## 事务与历史

选退课以 READ_COMMITTED 事务按“学生行 → 教学班行”取得悲观写锁。学生锁使同一学生对不同教学班的请求串行，教学班锁保护不同学生的名额竞争。业务校验、选课变化、人数和请求记录一起提交。

V5 为教学班增加 `open_at` / `close_at`，选退课采用 `[open_at, close_at)`：开放时刻允许，截止时刻拒绝。窗口和审计时间统一用 Java Instant，MySQL DATETIME(6) 约定存 UTC；服务在加锁后从注入的 Clock 取时间。生产 Clock.systemUTC，固定时间仅在 src/test 中通过 @Primary MutableClock 注入。种子窗口为 2020-01-01 至 2100-01-01 UTC，CLOSED 状态仍优先阻止选退。旧幂等键先回放原结果，已经退课的重复 DELETE 直接返回 204。

退课将 ACTIVE 改为 WITHDRAWN，保留历史；重新选课创建新行。MySQL 的生成列只对 ACTIVE 记录生成班级编号，联合唯一键保护有效关系，历史行的生成值为 NULL。有效课程重复、时间冲突和学分上限由持有学生锁的服务检查。

幂等键按学生隔离，长度为 1—80 个 ASCII 字母、数字、点、下划线或连字符。同键同班回放第一次成功时的响应快照；同键异班返回 409。退课后回放旧键只返回旧响应，不改变数据库；再次选课必须使用新键。课表始终反映当前提交状态。失败事务不会留下占位幂等记录，可用同键重试。

教学班 CLOSED 禁止新的选课和有效选课的退课；已评分记录禁止退课。教师只能为本人 CLOSED 班级中的 ACTIVE 记录评分，反复提交相同分数不产生额外业务变化。该样例不提供教务维护接口、生产级登录、跨学期选课、单双周、跨校区交通或先修课规则。

## 固定种子

| 班级 | 课程 | 教师 | 容量 | 星期 / 节次 | 规则场景 |
|---|---|---|---|---|---|
| 1 | 软件设计与体系结构（3 学分） | teacher1 | 1 | 周一 1—2 | 并发争抢最后名额 |
| 2 | 数据库原理（3 学分） | teacher1 | 30 | 周一 2—3 | 与班 1 时间冲突（端点包含） |
| 3 | Web 应用开发（3 学分） | teacher1 | 30 | 周二 1—2 | 不冲突，用于学分校验 |
| 4 | 软件测试（2 学分） | teacher2 | 30 | 周三 1—2 | 教师数据归属 |
| 5 | 计算机网络（3 学分） | teacher2 | 30 | 周四 1—2 | CLOSED |
| 6 | 软件设计与体系结构（3 学分） | teacher1 | 30 | 周五 1—2 | 与班 1 同课程不同时间 |

三个学生默认学分上限均为 12，数据库里 `student.id` 与 student1/2/3 对应。V3 独立补充班 6，专门验证“同学期同课程仅一个有效教学班”；V4 在 CLOSED 班 5 放入 student3 的 ACTIVE 未评分记录（人数 1），供 teacher2 打开页面录入成绩。修改样例数据应新增 Flyway 迁移，不修改已经执行的迁移。

## 集成验证

```powershell
$env:TEST_DB_URL='jdbc:mysql://127.0.0.1:13316/course_test?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
$env:TEST_DB_USER='course_app'
$env:TEST_DB_PASSWORD='Chapter10Db!'
mvn -B -ntp test
```

测试自动启用 src/test 内的 test 配置，专用 TEST_DB_URL 独立于运行应用的 DB_URL，避免打包时误清空演示库。测试通过真实随机端口 HTTP 调用应用，数据库查询使用请求提交后的独立连接，不使用 H2，不在测试方法外层包回滚事务。并发测试用 CountDownLatch 同时发出请求。回滚测试只在测试中临时创建一个拒绝幂等记录 INSERT 的触发器，以验证已经发出的选课 SQL 与人数更新一起回滚；finally 删除触发器。运行测试的账户需要专用测试库的 TRIGGER 权限。

若隔离测试实例开启二进制日志，管理员还需仅在该实例设置 `log_bin_trust_function_creators=1`，否则 MySQL 1419 会阻止测试触发器创建；应用账户无需 SUPER 权限。Maven Central 连接缓慢时，可使用本目录的可选镜像配置：`mvn -s maven-settings.xml -B -ntp test`。

原始 Surefire XML/TXT 与实际运行日志保存在 `evidence/`。生产程序没有故障注入端点，也没有测试触发器或测试 profile。
