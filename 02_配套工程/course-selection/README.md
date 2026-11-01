# 第十章学生选课系统配套工程

> 历史证据位置：本文件记载的evidence路径指原始测试记录.zip内部的相对路径，统一见配套资源包的04_测试与验收。重新执行自检时，当前结果写入工程根目录evidence。

固定版本：`chapter10-v1.0.0`。这是已构建、已在真实 MySQL 上验证的六功能教学基本版。下方列出固定版本资源与源码地址，二维码位置由编者按需填写。

工程下载地址：https://github.com/guoqingbin2021/software-design-architecture-ed2/releases/download/chapter10-v1.0.0/software-design-architecture-ed2-resources-v1.0.0.zip

源码仓库地址：https://github.com/guoqingbin2021/software-design-architecture-ed2

二维码地址：

## 实现范围

R01 教学班查询；R02 选课；R03 退课；R04 本人有效课表；R05 教师本人班级名单；R06 CLOSED 阶段成绩录入。身份来自认证上下文，实体统一 Student、Teacher、Course、TeachingClass、Enrollment。退课状态 WITHDRAWN，旧行保留，再选新建；旧幂等键只重放快照，不复活旧记录。

候补、可靠通知、微服务拆分、教务在线排课维护均未完成，属于选做；图中的扩展方案不是本工程已经交付的能力。固定学生、教师、课程、时间与教学班由 Flyway V1—V5 创建，不需要手工插入业务数据。

## 环境与空环境启动

已验证：Windows 11、JDK 17.0.9、Maven 3.9.9、MySQL 8.0.43 / InnoDB、READ-COMMITTED、Node 22.14.0、npm 10.9.2、Spring Boot 3.3.5、Vue 3.5.13、Vite 5.4.21。工具须放入 PATH。脚本要求 PowerShell 7（pwsh），本次自动复建也使用该版本。依赖首次下载需要网络。

1. 将工程解压到新目录；准备一个专用 MySQL 8.0 实例，本例端口 13316。
2. 在根目录执行 `Copy-Item .env.example .env`，按本机端口、账户修改 `.env`；开发库 course_demo，测试库 course_test。
3. 用管理员连接该专用实例：`mysql --host=127.0.0.1 --port=13316 -u root -p --default-character-set=utf8mb4`。进入客户端后执行 `source scripts/init-database.sql`。该步骤创建空库和教学用户，并允许测试创建临时故障触发器；应用账户不获 SUPER。
4. 先执行 `pwsh -File scripts/verify.ps1`，检查退出码和 `evidence/pipeline-result.txt`。此步构建并运行全部自动测试。
5. 第一终端执行 `pwsh -File scripts/start-backend.ps1`。首次构建后启动 JAR，Flyway导入固定数据，后端监听 18080。
6. 第二终端执行 `pwsh -File scripts/start-frontend.ps1`。`npm ci`按锁文件安装后启动 Vite，浏览器打开 `http://127.0.0.1:15173`。

再次执行 verify.ps1 前，先在本目录的后端、前端终端按 Ctrl+C 停止服务，避免 Windows 文件锁阻止 clean 或 npm ci。

如果 Maven 没有放在 PATH，可在 `.env` 添加 `MAVEN_CMD=C:/tools/apache-maven-3.9.9/bin/mvn.cmd`。启动脚本读取该文件，不需要修改业务源码。测试会清空 course_test 的业务数据，不能将 TEST_DB_URL 指向开发库或已有业务库。普通应用启动不删除开发数据。时间戳统一存 UTC，选退窗口为左闭右开，窗口边界测试使用固定 Clock。

`.env` 的 PORT 只控制后端端口。若修改18080，须同步修改 `frontend/vite.config.js` 的 `/api` 代理target；前端15173由该配置及package.json中的dev/preview命令固定。首次复现请使用示例端口，端口冲突时再同步调整相关配置。

## 账户与演示顺序

全部教学密码为 `Chapter10!`。Basic 凭据由前端显式加在 Authorization 头上，后端无状态；本机教学使用，正式系统应接入校园认证和 HTTPS。切换用户先退出。数据库示例密码为 `Chapter10Db!`，不包含个人凭据。

| 账户 | 固定身份与可验收任务 |
|---|---|
| student1 | 张敏，id=1；查询六班，选班1、查课表、退课 |
| student2 | 李明，id=2；与student1竞争班1唯一名额 |
| student3 | 王芳，id=3；初始化已在CLOSED班5，有一条未评分有效记录 |
| teacher1 | 陈老师，id=1；仅班1、2、3、6的名单；OPEN班禁止评分 |
| teacher2 | 刘老师，id=2；仅班4、5；在班5给student3录入成绩 |

班2与班1时间冲突；班3时间不同；班6与班1是同课程不同班。基本接口 `GET /api/enrollments/me` 只返回本人的 ACTIVE 记录；WITHDRAWN 历史保留在数据库供审计，没有额外的历史查询页面。

## 文件索引

本次整理只改变交付资料的位置，业务代码、测试、迁移和运行脚本保持原验证版本。教材中的docs/adr、docs/refactoring及需求追踪表保留原路径。历史证据集中压缩；本地运行verify.ps1会在工程根目录新建evidence保存本次结果。


[完整源文件清单](docs/FILE-MAP.md) · [API契约](docs/API.md) · [复现步骤](docs/REPRODUCTION.md) · [实测报告](../../04_测试与验收/验收报告.html#original-report) · [可编辑图件](docs/figures/README.md)

| 位置 | 内容 |
|---|---|
| backend/pom.xml、maven-settings.xml | Spring Boot依赖基线与可选HTTPS镜像 |
| backend/src/main/java/edu/textbook/selection/api | HTTP、DTO、错误体与requestId |
| backend/src/main/java/edu/textbook/selection/security | Basic认证、Actor身份、角色 |
| backend/src/main/java/edu/textbook/selection/service/EnrollmentService.java | 六功能、事务、固定锁顺序、快照幂等 |
| backend/src/main/java/edu/textbook/selection/domain | 同名实体、MeetingSlot、局部状态规则 |
| backend/src/main/java/edu/textbook/selection/repository | 查询、投影与数据库锁 |
| backend/src/main/resources/db/migration | V1结构、V2种子、V3重复课程、V4成绩场景、V5时间窗口 |
| backend/src/test | 真HTTP/MySQL、固定Clock、临时触发器故障；不打入生产JAR |
| frontend/src/App.vue、store.js、api.js、presenters.js | 六功能页面、状态协作、接口、显示契约 |
| frontend/tests | 查询乱序、写请求不确定状态、契约与页面状态测试 |
| scripts | 空库初始化、启动、一键构建测试、不变量查询 |
| docs | API、需求追踪、ADR、重构、评分和教师资源 |
| 历史验收记录 | 统一放在配套资源包的04_测试与验收；原始测试记录.zip保留原后端、前端及根evidence路径 |

工程代码是完整基本版；书中代码是标明源文件的阅读片段。首次 POST `/api/enrollments` 使用新的 `Idempotency-Key`；网络结果不确定时先查本人有效记录，若需重试必须沿用原键。新业务意图另用新键。HTTP 204 不带 JSON。主键是本例安全范围内的数字，不能直接照搬到超过 JavaScript 安全整数的大整数ID工程。

## 验证与提交

先阅读配套资源包的 `04_测试与验收/验收报告.html` 与工程 `docs/requirements-traceability.md`；按 `docs/REPRODUCTION.md` 在新目录、空数据库重新验证。`docs/teacher-resources/教师评价表.md` 的验收者、日期和签字由实际课堂验收者填写，自动测试不替代真人签字。

发布时不要上传 `.env`、node_modules、target、dist、本机数据库或工具目录；本次交付 ZIP 已排除这些本机与生成文件。完整实现、设计决策、重构记录、图件、实践指导、评分参考及原始证据均在同一个配套资源包中。使用与版本说明见包内 `05_使用与版本/使用说明.html`。


[配套资源总览](../../00_总览.html) · [实验指导与记录](../../03_实践指导/实验指导与记录.docx) · [教学组织与评价参考](../../03_实践指导/教学组织与评价参考.docx) · [原始记录索引](../../04_测试与验收/原始记录索引.html)


固定版本发布页：https://github.com/guoqingbin2021/software-design-architecture-ed2/releases/tag/chapter10-v1.0.0
