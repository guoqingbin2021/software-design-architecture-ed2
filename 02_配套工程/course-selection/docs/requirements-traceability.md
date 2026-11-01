# 需求、约束、实现与证据追踪

> 历史证据位置：本文件记载的evidence路径指原始测试记录.zip内部的相对路径，统一见配套资源包的04_测试与验收。重新执行自检时，当前结果写入工程根目录evidence。

R01—R06只表示功能；C01—C06表示横向约束。

| 编号 | 实现 | 对应验证及证据 |
|---|---|---|
| R01 教学班查询 | EnrollmentService.classes、CourseSelectionController、App.vue | authenticatesSeededIdentityAndQueriesClassesOnMysql；学生/教师页面截图 |
| R02 选课 | EnrollmentService.enroll、仓储锁、Enrollment.create | selectsQueriesTimetableAndWithdrawsWithHistory、容量/冲突并发及HTTP记录 |
| R03 退课 | EnrollmentService.withdraw、Enrollment.withdraw | 正常/重复退课、他人拒绝、历史保留、旧键重放 |
| R04 个人课表 | timetable、findActiveByStudent、store.js | 仅认证本人ACTIVE；退课后消失；页面截图 |
| R05 教师名单 | roster、ensureTeacher | 本人班成功、学生与非任课教师403 |
| R06 成绩录入 | recordScore、ensureClosedForGrades | 0/100/85、null/负数/超100/小数位、OPEN拒绝、重复PUT、退课并发 |
| C01 容量及计数 | 学生后教学班锁、同事务记录与计数 | concurrentStudentsCompeteForOneSeatWithoutOverbooking；独立SQL |
| C02 课程/时间/学分 | 同学生锁、同学期ACTIVE、BigDecimal、MeetingSlot | 冲突并发、重复课程、恰好上限及超限 |
| C03 有效唯一与历史 | V1生成列唯一键、WITHDRAWN历史 | databaseUniqueConstraintAllowsHistoryButRejectsTwoActiveRelations |
| C04 请求幂等 | enrollment_request快照、键目标校验 | 同键并发、退后旧键重放、换键重选、异目标409 |
| C05 身份及归属 | Actor、服务角色与所有权 | requiresAuthenticationAndEnforcesRoles、checksStudentAndTeacherOwnershipAndScoreBoundaries |
| C06 原子性及边界 | @Transactional、Clock、V5 UTC窗口 | databaseFailureRollsBackEnrollmentSeatAndIdempotencyRecord、窗口四边界及审计UTC |

原始JUnit报告：`backend/evidence/green/`；前端状态测试：`frontend/evidence/npm-test.log`；交付后完整复建：`evidence/pipeline-*.log`。最终方法名与数量可直接读取JUnit XML，未实现扩展不得填为通过。
