# 软件设计与体系结构 配套代码

本仓库提供《软件设计与体系结构》教材第1至10章的配套代码、实验数据和习题代码参考答案。第一至七章围绕零售业务学习职责分配、设计原则、设计模式与架构选择，第八至十章逐步完成学生选课系统。

## 下载与使用

在仓库主页选择 **Code → Download ZIP**，解压后按章节进入相应目录。也可以使用 Git：

```sh
git clone https://github.com/guoqingbin2021/software-design-architecture-ed2.git
cd software-design-architecture-ed2
```

每章的 `README.txt` 说明正文对应内容、运行环境、运行方法和验证方法；`answer_key/运行说明.txt` 说明习题代码答案的运行方式。设计题和文字题的参考答案见教材书末。

## 章节目录

| 目录 | 内容 | 使用入口 |
| --- | --- | --- |
| `chapter01` | 软件设计与体系结构概述 | 打印替换、金额计算与测试 |
| `chapter02` | GRASP设计原则与职责分配 | 零售结算的职责与协作 |
| `chapter03` | 面向对象设计原则 | 独立Java示例及固定输入验证 |
| `chapter04` | 创建型设计模式 | 分模块Maven示例 |
| `chapter05` | 结构型设计模式 | 分模块Maven示例 |
| `chapter06` | 行为型设计模式 | Java示例与`run-all.ps1` |
| `chapter07` | 软件体系结构风格与架构设计 | 架构方案评分数据及参考材料 |
| `chapter08` | 领域模型与数据访问设计 | 正文代码片段与SQL验证材料 |
| `chapter09` | 从选课请求到页面反馈 | `chapter9-enrollment/README.txt` |
| `chapter10` | 完成可验收的选课系统 | `course-selection/README.txt` |

第八章以代码片段为主；运行完整选课系统时，使用第九章或第十章的工程。各章示例和习题答案按各自说明独立运行，不应把不同模块中的同名类型合并编译。

## 环境与运行

Java示例采用JDK 17。Maven工程使用Maven 3.9；第九、十章还需要MySQL 8.0（CHECK约束至少需要8.0.16）、Node.js 22或24，以及PowerShell 7。依赖版本以各工程的`pom.xml`、`package.json`和锁文件为准。

- 第一、二章在本章目录运行`mvn test`。
- 第三章按本章说明编译后，以`--test`参数执行验证。
- 第四、五章进入具体模块运行`mvn -q compile exec:java`。
- 第六章运行`powershell -NoProfile -ExecutionPolicy Bypass -File ./run-all.ps1`。
- 第九章按`chapter9-enrollment/README.txt`配置环境后，使用`scripts/start.ps1`和`scripts/test.ps1`。
- 第十章按`course-selection/README.txt`配置开发库及专用测试库，再使用启动脚本与`scripts/verify.ps1`。

第九、十章的`.env.example`包含本机教学配置示例。复制为`.env`并按实际环境填写；测试和数据恢复脚本会重置业务数据，应使用相应说明指定的专用数据库。

## 文件核对

`SHA256SUMS.txt`列出本版文件的SHA-256校验值，清单本身不计入。仓库中的文件对应2026年11月10日整理的教材配套代码。
