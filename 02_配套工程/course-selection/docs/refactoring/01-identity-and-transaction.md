# 重构记录 A 身份与事务边界

> 历史证据位置：本文件记载的evidence路径指原始测试记录.zip内部的相对路径，统一见配套资源包的04_测试与验收。重新执行自检时，当前结果写入工程根目录evidence。

## 教学对照：身份与事务边界

对照性质：本节旧实现是用于教学比较的最小错误实现，没有实测或部署该反例；新版实际实现位于CourseSelectionController、Actor和EnrollmentService。需求R02/R03，约束C01/C05/C06。后面的“时间窗口偏移”另记本次实际发现并修复的问题。

修改前：

```java
// 教学反例：不可信studentId + 独立写入步骤
var row = enrollmentRepository.save(new Enrollment(request.studentId(), request.classId()));
classRepository.incrementCount(request.classId());
return row;
```

该结构让页面任意选择学生，且第二步失败可能留下不完整状态，两个步骤也不能共同防超选。

修改后：

```java
// CourseSelectionController实际入口
return service.enroll(actor, request.teachingClassId(), key);
// EnrollmentService顶层事务
var student = students.lockById(actor.personId()).orElseThrow(ApiFailure::notFound);
var target = classes.lockById(classId).orElseThrow(ApiFailure::notFound);
// 完整规则检查后，人数、Enrollment、请求快照同事务提交
```

回归证据：未实现业务阶段真实MySQL红灯保存在backend/evidence/red（用于证明验收测试会失败，不是运行上述反例的记录）；最终同一契约测试通过见green。具体核对伪造studentId400、他人退课403、容量双请求一成一拒、触发器故障后计数/记录/请求表均0。

收益：身份和原子性有统一入口，Controller不直接写持久化状态。代价：额外Actor、应用服务和锁等待。原始测试成功/失败记录、收益与代价分别保存，不能仅写原则名称。

## 本次实际后端问题：时间窗口偏移

这是本工程开发中实际出现的缺陷。R02选课和R03退课共用教学班的选退课窗口，属于C06“原子性及边界”；截止后旧幂等请求的处理同时关联C04“请求幂等”。窗口规定为UTC绝对时刻的左闭右开区间 `[open_at, close_at)`：开放时刻允许，截止时刻拒绝；CLOSED状态仍禁止新的选退课。

### 失败现象与定位

2026-10-31的真实MySQL测试将班3的 `open_at` 写成 `2026-10-31 10:00:00`，`close_at` 写成 `12:00:00`，二者均按UTC解释。测试通过仅在 `src/test` 中定义的MutableClock，把当前时间固定到 `10:00:00Z`。开放时刻的选课却未成功。随后增加直接检查JPA加载值的断言，得到：

```text
[Extracted: openAt]
expected: 2026-10-31T10:00 (java.time.LocalDateTime)
 but was: 2026-10-31T18:00 (java.time.LocalDateTime)
Tests run: 1, Failures: 1, Errors: 0
BUILD FAILURE
```

原始记录是 [09-window-timezone-diagnosis.log](../../../../04_测试与验收/原始记录索引.html#record-dc6030a3b699)。该断言把问题定位到数据库值与实体时间值之间：SQL中的10:00被实体读成18:00，差8小时。在本机JVM默认Asia/Shanghai、连接URL含 `serverTimezone=Asia/Shanghai`、Hibernate配置 `hibernate.jdbc.time_zone=UTC` 的组合下，用不携带时区的LocalDateTime表示窗口的绝对时刻，经过JPA/JDBC时间转换后产生了这一偏移。服务用UTC Clock取得的10:00与加载成18:00的开放时间比较，错误地判定“尚未开始”。

### 修改前后代码

以下是本次修复前代码的摘录，省略注解以外的实体字段和其他业务检查。时间窗实现当时已经注入Clock，但比较值与持久化值使用LocalDateTime。

```java
// TeachingClass：修复前的实际字段与边界判断
@Column(name="open_at", nullable=false)
private LocalDateTime openAt;
@Column(name="close_at", nullable=false)
private LocalDateTime closeAt;

public void ensureSelectionWindow(LocalDateTime now) {
    ensureOpen();
    if (now.isBefore(openAt))
        throw new BusinessConflict("WINDOW_NOT_OPEN", "选退课时间尚未开始");
    if (!now.isBefore(closeAt))
        throw new BusinessConflict("WINDOW_CLOSED", "选退课时间已经截止");
}

// EnrollmentService：取得锁后检查
var now = LocalDateTime.now(clock);
target.ensureSelectionWindow(now);
```

修复将窗口和审计时间统一为Instant，服务从同一个可注入Clock取得绝对时刻。生产Clock由 `service/TimeConfiguration.java` 提供：

```java
@Bean
public Clock clock() { return Clock.systemUTC(); }
```

`domain/TeachingClass.java` 的当前实现摘录如下；比较条件保留原定的左闭右开语义。

```java
@Column(name="open_at", nullable=false)
private Instant openAt;
@Column(name="close_at", nullable=false)
private Instant closeAt;

public void ensureSelectionWindow(Instant now) {
    ensureOpen();
    if (now.isBefore(openAt))
        throw new BusinessConflict("WINDOW_NOT_OPEN", "选退课时间尚未开始");
    if (!now.isBefore(closeAt))
        throw new BusinessConflict("WINDOW_CLOSED", "选退课时间已经截止");
}
```

`service/EnrollmentService.java` 中的选课顺序如下，属于等价控制流摘录；省略了幂等键格式、学生状态和其他选课规则的检查。

```java
var student = students.lockById(actor.personId()).orElseThrow(ApiFailure::notFound);
var previous = requests.findByStudentIdAndRequestKey(student.getId(), key);
if (previous.isPresent()) {
    var saved = previous.get();
    if (saved.getClassId() != classId)
        throw new BusinessConflict("IDEMPOTENCY_CONFLICT", "同一幂等键不能用于其他教学班");
    return decode(saved.getResponseJson());
}
var target = classes.lockById(classId).orElseThrow(ApiFailure::notFound);
var now = clock.instant();
target.ensureSelectionWindow(now);
// 完整规则通过后，Enrollment.create(student, target, now)、人数与请求快照同事务提交
```

服务在取得锁后取时间，避免把排队等待前的时间用作新操作的窗口依据。旧键先回放原成功快照，所以原操作在窗口内成功后，即使重试到达时已经截止，也不会被当作一次新的选课。

退课同样先锁学生、再锁教学班，再调用 `enrollment.withdraw(clock.instant())`。`domain/Enrollment.java` 先对已经WITHDRAWN的记录直接返回，再拒绝已评分记录，随后执行 `teachingClass.ensureSelectionWindow(now)`、释放名额并记录退课时间。这样，有效选课仍受时间窗约束，重复退课可以保持204。

`Enrollment.enrolledAt`、`Enrollment.withdrawnAt` 和 `EnrollmentRequest.createdAt` 也改为Instant，使用注入Clock给出的时间。V5迁移中的窗口列仍为MySQL `DATETIME(6)`，工程明确约定存UTC，并以 `CHECK (open_at < close_at)` 保护窗口定义；Hibernate继续使用UTC配置。

### 回归结果与证据

回归方法为 [CourseSelectionIntegrationTest.selectionWindowIsLeftClosedRightOpenAndOldKeysReplayAfterDeadline](../../backend/src/test/java/edu/textbook/selection/CourseSelectionIntegrationTest.java)。它运行真实HTTP和MySQL，以测试专用的 `@Primary MutableClock` 控制时刻；生产包没有改时间的HTTP入口。

| 固定UTC时刻或操作 | 实际验证的边界 |
|---|---|
| 09:59:59.999999，开放前 | 新选课与有效选课退课均为409，`WINDOW_NOT_OPEN` |
| 10:00:00，开放时 | 选课200，退课204 |
| 11:59:59.999999，截止前 | 选课200，退课204 |
| 12:00:00，截止时 | 新选课与有效选课退课均为409，`WINDOW_CLOSED` |
| 截止后重放窗口内已成功的同键请求 | 返回原快照，不新增选课；有效记录数和班级人数仍各为1 |
| SQL写入窗口10:00，JPA重新加载 | 精确等于 `Instant.parse("2026-10-31T10:00:00Z")` |
| 开放时选课与退课后的独立SQL查询 | `DATE_FORMAT(enrolled_at,...)`、`DATE_FORMAT(withdrawn_at,...)` 均为UTC `2026-10-31T10:00:00.000000` |

以下是已经执行并保留的记录，本次补文档没有重新运行测试：

| 证据 | 结果与用途 |
|---|---|
| [09-window-timezone-diagnosis.log](../../../../04_测试与验收/原始记录索引.html#record-dc6030a3b699) | 单用例1次断言失败，保留10:00读成18:00的原始错误 |
| [10-mysql-final-green.log](../../../../04_测试与验收/原始记录索引.html#record-a00a8ca01dcb) | 改用Instant后，19项全部通过，失败/错误均0 |
| [11-final-package.log](../../../../04_测试与验收/原始记录索引.html#record-9c7359d84eb2) | 加上审计列UTC精确值断言后，19项全部通过并打包成功 |
| [green原始JUnit XML](../../../../04_测试与验收/原始记录索引.html#record-7d5556a5ae34) | 保留最终方法名、耗时及0失败/0错误结果 |
| [干净目录构建日志](../../../../04_测试与验收/原始记录索引.html#record-78e2dd179ab3)、[新空库启动日志](../../../../04_测试与验收/原始记录索引.html#record-f36f98b96a2d) | 同主机新目录重建19项通过；新空库course_repro成功执行V1—V5，核对迁移与构建可复现 |

### 收益与代价

修复使窗口比较和审计字段使用同一种绝对时间语义，消除了本次实测的8小时偏移；固定Clock让开放与截止边界可以精确重现，无需等待真实时间。数据库原始值、实体加载值、HTTP结果和最终人数都有对应断言，能够发现仅检查页面提示时遗漏的问题。

代价是工程必须持续遵守UTC存储约定，展示本地时间时显式选择时区；手工SQL和导入数据也需先明确原始时区。`DATETIME(6)` 的精度为微秒，边界测试据此选择六位小数。若接入已有本地时间数据，应先核对并转换其时区，不能只更改Java字段类型。这些约定需要与V5迁移、Clock配置和上述回归用例一起维护。
