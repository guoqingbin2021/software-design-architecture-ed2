# 重构记录 B 当前关系与操作结果

> 历史证据位置：本文件记载的evidence路径指原始测试记录.zip内部的相对路径，统一见配套资源包的04_测试与验收。重新执行自检时，当前结果写入工程根目录evidence。

旧依据：本轮修订前第十章代码10-3确实按当前关系判断并重新activate原行；原稿备份保存在教材工作目录的execution-20261031/backup。需求R02/R03，约束C03/C04/C06。

修改前：

```java
Selection record = old == null ? new Selection(student, target) : old;
record.activate(clock.instant());
```

修改后：

```java
var previous = requests.findByStudentIdAndRequestKey(student.getId(), key);
if (previous.isPresent()) {
    var saved = previous.get();
    if (saved.getClassId() != classId) throw new BusinessConflict("IDEMPOTENCY_CONFLICT", "同一键不能换目标");
    return decode(saved.getResponseJson());
}
// 新意图通过规则后新建Enrollment；保存与业务同事务的快照
var row = enrollments.saveAndFlush(Enrollment.create(student, target, now));
return remember(student.getId(), key, classId, EnrollmentResult.from(row));
```

数据库：V1生成列仅限制一条ACTIVE，WITHDRAWN历史可多行；enrollment_request按student_id/request_key唯一。

回归证据：replaysRequestWithoutResurrectingWithdrawnEnrollment先选退，旧键响应逐字相同且有效数仍0，新键得到不同id；concurrentSameIdempotencyKeyReturnsOneCommittedResult两响应相同且记录/请求表各1；databaseFailureRollsBackEnrollmentSeatAndIdempotencyRecord三项回滚。源骨架未实现时的红灯和最终green均保留，没有捏造旧代码运行结果。

收益：历史可审计，旧请求不误变成新选课。代价：快照存储、客户端原键管理、历史清理策略；界面必须提示快照需以当前课表确认。详见ADR 002。

## 本次实际前端缺陷与修复

关联需求：R02 选课、R03 退课、C04 请求幂等。本节记录的是本轮前端开发中实际出现、由回归测试复现并修复的问题。旧片段来自修复前的 `frontend/src/store.js`，不是为说明问题而虚构的反例；以下只省略同一函数中无关的状态设置与异常分支。

真实触发条件：第一次选课已在服务端提交，但客户端没有收到响应；该选课随后被退掉。客户端沿用原幂等键重试时，后端正确回放第一次成功的 ACTIVE 响应快照，当前 `GET /api/enrollments/me` 却已无此教学班。快照证明原请求曾成功，不能证明当前仍处于已选状态。

修复前，`sendEnrollment()` 的实际成功分支为：

```javascript
await api.enroll(target.teachingClassId, target.key);
if (startEpoch !== epoch) return;
confirmEnrollmentSuccess('选课成功。', username);
await Promise.all([loadClasses(), loadMyEnrollments()]);
```

这里确实已经刷新个人记录，但没有使用 GET 的返回值修正提示。因此即使刷新后的有效记录为空，“选课成功。”仍保留在页面上；后端没有复活历史，前端却把历史成功误报成当前成功。

修复后，在提交前固定 `const submittedKey = target.key`，成功分支的实际片段为：

```javascript
await api.enroll(target.teachingClassId, target.key);
if (startEpoch !== epoch) return;
confirmEnrollmentSuccess('选课请求已处理，正在查询当前记录。', username);
const [, rows] = await Promise.all([loadClasses(), loadMyEnrollments()]);
if (startEpoch !== epoch || target.key !== submittedKey) return;
// An idempotency replay is the original success snapshot, not current enrollment state.
target.message = rows == null ? '选课请求已处理，请刷新个人记录查看当前状态。'
  : rows.some((row) => row.teachingClassId === target.teachingClassId && active(row)) ? '选课成功。'
  : '选课请求已处理，但当前有效记录中未包含此教学班；请核对当前选课。';
```

`active(row)` 在实际实现中判断 `row.status === 'ACTIVE'`。`confirmed` 表示提交请求已有明确处理结果；当前是否已选由随后读取的有效记录决定。GET 失败时不把它当成提交失败，不新建幂等键，也不自动发送新的 POST；登录代次与原请求号检查防止迟到的查询覆盖后续操作。

回归用例为 `frontend/tests/store.test.js` 中的“旧键回放历史成功但当前已退课时，页面不宣称当前选课成功”。测试注入返回 `{id:7,teachingClassId:1,status:'ACTIVE',score:null}` 的选课 API，以及返回空数组的个人记录 API，复现“历史成功快照与当前状态不同”的控制流；这是一项前端状态回归测试，不把它冒充成浏览器跨标签页实测。断言提交状态仍为 `confirmed`，提示包含“当前有效记录中未包含”，且不包含“选课成功”。

| 阶段 | 实际结果 | 工程根目录下的完整证据路径 |
|---|---|---|
| 修复前先补回归用例 | 仅运行 store 测试：19 项，18 通过、1 失败。该用例实际得到“选课成功。”，未匹配 `/当前有效记录中未包含/`。 | `frontend/evidence/replay-snapshot-message-red.log` |
| 修复后运行全部前端测试 | Node 22.14.0 下 `npm test`：32 项，32 通过、0 失败；包括上述用例、原键重试、查询确认失败和重复点击保护。 | `frontend/evidence/npm-test.log`；`frontend/evidence/versions.log` |
| 构建与范围记录 | `npm run build` 成功；验证报告区分前端可控 API 测试与真实后端/浏览器验收。 | `frontend/evidence/npm-build.log`；`frontend/evidence/VERIFICATION.md`；`frontend/evidence/BACKEND-CONTRACT-REVIEW.md` |

收益：R02 的成功提示与当前有效选课一致，R03 的退课结果不会被旧快照在界面上掩盖，C04 的原键回放仍保持原有语义。代价：前端必须区分“请求已处理”与“当前已选”，最终提示要等待已有 GET 查询完成，并处理查询失败、登录切换和请求切换；没有增加成功路径的 HTTP 查询次数，因为旧实现本来就执行该 GET。后端历史快照和当前课表接口均无需为此修改。
