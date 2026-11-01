# 教学选课前端

> 历史证据位置：本文件记载的evidence路径指原始测试记录.zip内部的相对路径，统一见配套资源包的04_测试与验收。重新执行自检时，当前结果写入工程根目录evidence。

第十章的 Vue 单页配套工程。依赖固定为 Vue **3.5.13**、Vite **5.4.21**、`@vitejs/plugin-vue` **5.2.4**，随工程提供 `package-lock.json`。Node.js 18 及以上可运行，工程验收使用 Node **22.14.0** 与其自带 npm。

## 启动与构建

```powershell
npm ci
npm test
npm run dev
```

打开 `http://127.0.0.1:15173`。开发服务将 `/api` 代理到 `http://127.0.0.1:18080`，请先启动后端。

```powershell
npm run build
npm run preview
```

`dist/` 是生产构建产物。预览服务仍使用相同 API 代理；实际部署时由同源反向代理将 `/api` 转发至后端。不要将 Vite 开发服务当成生产服务器。

预置账号：`student1`、`student2`、`student3`、`teacher1`、`teacher2`，统一密码 `Chapter10!`。页面可一键填入演示账号。HTTP Basic 是本项目的教学认证方式；凭据只放在内存和当前标签页的 `sessionStorage`，退出登录后清除。未确认的选课请求号按账号保留到确认或收到明确的业务拒绝，防止刷新后丢失。

## 功能与状态

- 学生：检索教学班、选课、退课、查看当前有效选课、查看按星期和节次排序的个人课表。历史保留在数据库供审计，不通过当前课表接口展示。
- 教师：选择本人授课的教学班、查询学生名单；仅 CLOSED 教学班可保存 0–100 且最多两位小数的成绩。可使用 teacher2 的教学班 5 演示成绩录入。
- 选课状态为 ACTIVE/WITHDRAWN；OPEN 且未评分的 ACTIVE 记录可退课。CLOSED 教学班仍出现在有效课表中，但不可退课。
- 读取状态为 `idle/loading/data/error`，请求序号与登录代次共同阻止旧响应覆盖新页面。
- 提交状态为 `idle/submitting/confirmed/uncertain`。提交中禁用重复操作；网络断开、超时和 5xx 写入响应均保守地标记为结果待确认。
- 选课超时后先查询个人记录。查到有效选课即确认成功；查不到时保留原幂等键，仅在用户点击“用原请求号重试”并再次查询之后重发原请求。没有自动 POST 重试。
- 退课结果待确认时查询个人记录；成绩结果待确认时查询本班名单。查询失败不视为写入失败。

## 文件

| 文件 | 责任 |
|---|---|
| `src/api.js` | Basic 身份、JSON、12 秒超时、统一错误、接口调用 |
| `src/store.js` | 查询乱序保护、提交互斥、选课幂等键、查询确认 |
| `src/App.vue` | 学生和教师工作台、会话凭据、页面反馈 |
| `src/presenters.js` | 中文状态、时间展示与课表排序 |
| `tests/` | 使用 Node 内置测试器验证真实状态逻辑，注入可控异步 API |
| `evidence/` | 本次安装、测试、构建与红灯阶段日志 |

测试不模拟浏览器布局；与真实后端的浏览器验收证据由工程总报告提供。接口字段见 [API-CONTRACT.md](./API-CONTRACT.md)。

公开下载地址：https://github.com/guoqingbin2021/software-design-architecture-ed2/archive/refs/tags/chapter10-v1.0.1.zip

资源二维码（指向固定版本页）：[查看PNG](../docs/resources-download-qr.png)
