第10章 完成可验收的选课系统：配套代码与材料

一、正文对应内容

对应第十章选课系统的领域模型、并发控制、前后端集成与系统验证。

二、目录说明

course-selection/backend：Spring Boot后端。
course-selection/frontend：Vue前端。
course-selection/examples：正文HTTP请求与SQL。
course-selection/scripts：初始化、启动、验证与恢复脚本。
course-selection/README.txt：账户、数据条件和完整实验步骤。

三、运行环境

JDK 17、Maven 3.9、MySQL 8.0、Node.js 22或24、PowerShell 7。
依赖与锁文件以工程中的pom.xml、package.json和package-lock.json为准。

四、运行方法

进入course-selection目录，按README.txt建立独立开发库和测试库，并配置
.env。分别在两个终端执行：
    pwsh -File scripts/start-backend.ps1
    pwsh -File scripts/start-frontend.ps1
访问地址、初始账户和数据恢复方法见工程README.txt。

五、验证方法

在工程目录执行：
    pwsh -File scripts/verify.ps1
脚本执行前后端测试与构建。TEST_DB_URL必须指向专用测试库，测试会重置
该库的业务数据。操作完成后还可按说明独立核对数据库不变量。

六、习题答案

answer_key保存本章习题的代码或数据参考答案，运行方法见其中的运行说明.txt。
题目条件、分析过程和非代码答案见全书书末。答案与本章示例分开运行。
