# 前端 API 契约

所有请求使用 `/api` 同源路径，`Authorization: Basic <base64(username:password)>`。JSON 请求附带 `Content-Type: application/json`；选课另外附带 `Idempotency-Key`。服务端必须独立验证角色和数据归属，页面按钮不构成授权。

| 方法与路径 | 请求 | 成功响应 |
|---|---|---|
| `GET /api/me` | 无 | `{username, role, personId, name}`，`role` 为 `STUDENT` 或 `TEACHER` |
| `GET /api/teaching-classes` | 无 | 教学班数组；教师仅返回本人的教学班 |
| `POST /api/enrollments` | `{teachingClassId:1}`；`Idempotency-Key` 头 | `{id, teachingClassId, courseName, status, score}` |
| `GET /api/enrollments/me` | 无 | 当前学生的 ACTIVE 有效选课数组；WITHDRAWN 历史保留在数据库供审计 |
| `DELETE /api/enrollments/{id}` | 无 | `204 No Content` |
| `GET /api/teaching-classes/{id}/roster` | 无 | `{id, studentId, studentName, status, score}[]`；`id` 为选课记录 ID |
| `PUT /api/enrollments/{id}/score` | `{score:85}`；0–100，最多两位小数 | 更新后的选课记录；仅本人 CLOSED 教学班的 ACTIVE 选课可录入 |

教学班字段：`id, courseCode, courseName, teacherName, capacity, enrolledCount, dayOfWeek, startPeriod, endPeriod, credits, status`。`dayOfWeek` 使用 1–7（周一至周日），节次为整数，教学班状态 `OPEN/CLOSED`。

个人选课记录字段：`id, teachingClassId, courseCode, courseName, teacherName, capacity, enrolledCount, dayOfWeek, startPeriod, endPeriod, credits, status, score, classStatus`。有效选课使用 `ACTIVE`，退课历史使用 `WITHDRAWN`。成绩未录入时为 `null`；`classStatus` 是教学班的 `OPEN/CLOSED` 状态。仅 OPEN 且未评分的 ACTIVE 选课可退课。

错误响应为 `{code,message,requestId}`；预期 HTTP 状态为 400、401、403、404、409，方法不支持与媒体类型不支持分别为 405、415。页面显示 `message` 和可用的 `requestId`。响应体异常、网络错误、超时以及 5xx 写请求不等同于业务拒绝，页面会进入 `uncertain` 并提供查询确认。选退课时间窗由服务端在事务内校验，尚未开放或已截止时返回 409 业务错误；DTO 不提供在线排期维护。

同一学生的同一选课请求重试必须使用同一幂等键。页面将未确认选课的 `{teachingClassId,key}` 存入按账号区分的会话存储；只有收到明确结果或个人记录确认成功后才清除。重试前再次 GET 查询，防止把已完成的选课当成失败。服务端应永久或按其文档规定的有效期保存幂等记录，禁止将同一键再次视为新操作。

示例：

```http
POST /api/enrollments
Authorization: Basic <演示凭据>
Content-Type: application/json
Idempotency-Key: 71a52b55-750e-492b-a655-987bf731c231

{"teachingClassId":1}
```

若此请求超时，查询 `GET /api/enrollments/me`。查到 `teachingClassId=1,status=ACTIVE` 则确认成功；查询未确认时保留上面的同一请求号，等待再次查询或用户显式重试。

