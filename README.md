# 软件设计与体系结构 第二版配套资源

本仓库提供教材第十章学生选课系统的完整工程、实践指导、设计案例、教材图件和测试验收资料。

**[下载配套资源 ZIP](https://github.com/guoqingbin2021/software-design-architecture-ed2/releases/download/chapter10-v1.0.0/software-design-architecture-ed2-resources-v1.0.0.zip)** · **[固定版本与校验文件](https://github.com/guoqingbin2021/software-design-architecture-ed2/releases/tag/chapter10-v1.0.0)**

完整解压后双击 `00_总览.html`，即可离线查阅全部配套资料。GitHub文件页会显示HTML源码；本地浏览器可直接阅读。仓库沿用本地交付资料的目录编号，书稿目录不在公开范围。

## 资源导航

| 内容 | 入口 |
|---|---|
| 工程安装、初始化和运行 | [工程README](02_配套工程/course-selection/README.md) |
| 代码与测试位置 | [文件索引](02_配套工程/course-selection/docs/FILE-MAP.md) |
| 接口路径、字段与错误响应 | [API契约](02_配套工程/course-selection/docs/API.md) |
| 两份设计决策 | [ADR](02_配套工程/course-selection/docs/adr/) |
| 两份重构记录与前后对照 | [重构案例](02_配套工程/course-selection/docs/refactoring/) |
| 功能、约束与测试对应 | [需求追踪](02_配套工程/course-selection/docs/requirements-traceability.md) |
| 9组PNG与可编辑drawio图件 | [图件](02_配套工程/course-selection/docs/figures/) |
| 实验记录、教学组织与评价参考 | [实践指导](03_实践指导/) |
| 验收报告及89份原始记录 | [测试与验收](04_测试与验收/) |
| 使用方式、版本与校验 | [使用说明](05_使用与版本/使用说明.md) |

## 运行工程

使用PowerShell 7、JDK 17、Maven 3.9、MySQL 8.0及Node.js 22。进入 `02_配套工程/course-selection`，按工程README准备专用开发库和测试库，再依次初始化、自检、启动。依赖首次安装需要网络。再次自检前先停止本目录的前后端服务。

工程实现教学班查询、选课、退课、个人课表、教师名单和成绩录入六项功能。固定账户与业务数据均为教学样例。候补、可靠通知、服务拆分属于教材选做内容，基本工程未实现。

## 版本和验证记录

- 教材对应源码版本：`chapter10-v1.0.0`。本标签固定保留；后续修改另建版本。
- 2026年10月31日完成19项真实HTTP/MySQL集成测试、32项前端测试、六功能浏览器验证、前后端构建及新目录空库复建。
- 本次GitHub发布只调整公开资料与阅读入口。业务源码、测试、依赖配置及运行脚本与已验证版本逐文件一致，未重新执行上述业务测试。
- 原始日志、JUnit报告、页面截图与SQL结果集中于 `04_测试与验收/原始测试记录.zip`，保留原始路径。运行自检时会生成自己的实验记录。

本仓库只公开工程和配套资料，教材整书及第十章书稿留在编者本地。课堂记录与签字由实际执行者填写。Release附件配有SHA-256；仓库文件清单见 [SHA256SUMS.txt](SHA256SUMS.txt)。
