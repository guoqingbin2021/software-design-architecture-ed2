第九章习题代码参考答案

入口：Chapter09Answers.mjs；检查：Chapter09Answers.test.mjs。
环境：Node.js 22或更新版本，无第三方依赖。
在本目录运行：node --test Chapter09Answers.test.mjs

9-8 validateBody严格检查teachingClassId；selectionInput只从已认证Actor取身份；validateSuccessDto核对id、目标和ACTIVE。
9-9 LatestQuery保护data、error、loading三个分支，退出登录使旧响应全部失效。
9-10 recoverSelection先查询原键，再读当前课表；404仍待确认，不生成新键，不发新POST。
CONFIRMED_READ_FAILED说明原写入已确认、课表读取未完成，应提供重读入口。
恢复快照使用id与teachingClassId，不使用enrollmentId。

退课恢复的前提是同一轮关系期间未重新激活。记录1001重新激活后，相同id不能区分新旧轮次；迟到旧DELETE仍可能退掉新轮。
本函数仅提供恢复选项，不能承诺跨轮旧DELETE安全。原chapter9-enrollment工程MySqlContract可在真实HTTP和MySQL中复现此限制；第十章以每轮新id隔离旧请求。

实际执行：Node内置测试以可控Promise固定响应交错，检查输入、可信身份前提、成功DTO、乱序、退出和恢复分支。
适用范围：纯函数与内存状态；没有在本答案目录实现认证、HTTP服务器或数据库事务。Actor是调用前已满足的可信上下文，不是客户端传参。
