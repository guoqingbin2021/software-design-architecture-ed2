第9章 从选课请求到页面反馈：配套代码与材料

一、正文对应内容

对应第九章学生选课、退课、课表与页面反馈的连续案例。教师名单与成绩
留到第十章。关系复用id，重复退课保证限于同一轮关系期间未重新激活；
若已重新激活，迟到的旧DELETE仍会取消新一轮关系，详见工程README.txt。

二、目录说明

chapter9-enrollment/backend：Spring Boot后端。
chapter9-enrollment/frontend：Vue前端。
chapter9-enrollment/database、scripts：数据与运行脚本。
chapter9-enrollment/README.txt：启动、账户、接口和数据恢复的详细说明。

三、运行环境

JDK 17、Maven 3.9、MySQL 8.0、Node.js 22或24、PowerShell 7。
CHECK约束要求MySQL至少8.0.16；容器启动方式还需要Docker Desktop。

四、运行方法

进入chapter9-enrollment目录，按该目录README.txt配置.env后执行：
    pwsh -File scripts/start.ps1
本机开发方式、端口和教学账户也见该说明。

五、验证方法

在工程目录执行：
    pwsh -File scripts/test.ps1
加-Integration可运行Docker MySQL集成测试；使用本机专用测试库的配置
见工程README.txt。集成测试会重建业务数据，只能使用专用测试库。
