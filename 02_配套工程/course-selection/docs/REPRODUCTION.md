# 从新目录复现

> 历史证据位置：本文件记载的evidence路径指原始测试记录.zip内部的相对路径，统一见配套资源包的04_测试与验收。重新执行自检时，当前结果写入工程根目录evidence。

固定版本 `chapter10-v1.0.0`。工程下载地址：

## 准备与执行

1. 解压完整交付 ZIP，进入 `02_配套工程/course-selection`。安装 README 指定的 PowerShell7、JDK17、Maven3.9、MySQL8.0、Node22，确认命令可在 PATH 中找到。
2. 准备专用 MySQL 实例。示例监听13316；不要在已有业务数据库上运行初始化或测试。执行 `Copy-Item .env.example .env` 并填写连接信息。
3. 在工程根目录执行 `mysql --host=127.0.0.1 --port=13316 -u root -p --default-character-set=utf8mb4`。进入客户端后执行 `source scripts/init-database.sql`，再执行 `exit` 回到 PowerShell。
4. 执行 `pwsh -File scripts/verify.ps1`。后端19项、前端32项均应通过，JAR与前端生产构建均成功，`evidence/pipeline-result.txt` 应为 PASS。
5. 第一终端执行 `pwsh -File scripts/start-backend.ps1`，等待18080监听及5个Flyway迁移成功；第二终端执行 `pwsh -File scripts/start-frontend.ps1`，打开 `http://127.0.0.1:15173`。
6. 按下表完成页面操作，再用新 MySQL 客户端连接开发库执行 `source scripts/verify-invariants.sql`。六条查询均应没有违规行。

再运行构建前，先在前后端终端按 Ctrl+C 停止本目录服务。Windows 不允许 Maven clean 删除运行中的 JAR；npm ci 也可能与开发服务器占用的文件冲突。测试只使用 course_test，不重置 course_demo。已操作过的开发库不再是初始种子状态；需要重复“首次启动”时，使用另一个空的专用开发库，并给应用账号授予该库权限。

| 功能 | 操作 | 预期与核对 |
|---|---|---|
| R01 查询 | student1登录 | 六个教学班；teacher1只看到1/2/3/6，teacher2只看到4/5 |
| R02 选课 | student1选择班1 | 成功；人数1/1；本人有效记录1条 |
| R04 课表 | 打开个人课表 | 周一第1–2节，软件设计与体系结构，3学分 |
| R03 退课 | 我的选课→退课 | 本人有效记录变空；班1人数0；数据库原行WITHDRAWN |
| R05 名单 | 退出，teacher2登录，选择班5 | 一条王芳/student3记录，初始成绩为空 |
| R06 成绩 | 录入85并保存、刷新 | 页面与数据库均为85；teacher1访问班5返回403 |

密码统一为 `Chapter10!`。课堂并发、幂等、回滚、权限与边界要求见自动测试和 `requirements-traceability.md`；不要通过直接改业务表来模拟正常使用。教师资源中的姓名、日期、签字由实际验收者填写。

## 本次已执行的复建

2026-10-31，在同一台Windows主机上另建干净目录；不复制 `.env`、node_modules、target、dist。复制 `.env.example` 后独立配置，开发库使用新建空库 `course_repro`，后端端口18081，测试仍使用专用 `course_test`。

`verify.ps1`退出0，后端19/19、前端32/32，构建成功。随后从新目录JAR启动，V1—V5全部成功；API显示学生6班、teacher1四班、teacher2两班；新数据库中仅有种子班5/student3的ACTIVE未评分记录。该临时18081服务已停止。原始证据在 `evidence/clean-reproduction`。

这是同一主机上的新目录与空库自动复建记录。另一名真实学生的无口头指导课堂验收仍应由课程组织者安排；本包不代填他人的签字。

## 复现记录表

复现者：

日期：

操作系统与工具版本：

工程版本与SHA-256：

数据库/端口：

实际命令与退出码：

页面、HTTP及SQL证据位置：

遇到的问题、原因和处理：

最终结论与签字：
