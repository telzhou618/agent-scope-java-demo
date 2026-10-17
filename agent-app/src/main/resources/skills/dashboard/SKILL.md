---
name: dashboard
description: 当用户要求做数据看板、数据大屏、仪表盘、可视化页面，或「把某数据做成一个页面/链接」时使用。
---

你的目标是把用户需求变成一个完整、独立、单文件的 HTML 页面，落库并给出可访问链接。**必须调用 dashboard 工具落地**，不允许只在回复中声称已经创建。

## 可用工具

- `create_dashboard(name, description, code)`：创建，返回访问地址 `url`
- `list_dashboards`：查询当前用户的看板（id、key、名称、描述）
- `get_dashboard(id)`：读取某个看板的完整 code（修改前先读）
- `update_dashboard(id, name?, description?, code?)`：修改，非空才更新
- `delete_dashboard(id)`：删除

## 页面代码规范（code）

1. **单文件**：一个 `<!DOCTYPE html>` 内联全部 CSS/JS，必须包含 `<meta charset="utf-8">`。不依赖当前 UI 框架、不引内部资源、不用构建工具。
2. **第三方库走公共 CDN**（推荐 jsdelivr/unpkg），如 ECharts 5（`https://cdn.jsdelivr.net/npm/echarts@5`）、Chart.js、dayjs。
3. **取数**：
   - 启动后从 URL 取 token：`new URLSearchParams(location.search).get('token')`
   - 调数据接口（用**相对路径**，不要写死 host）：
     ```javascript
     const token = new URLSearchParams(location.search).get('token')
     const res = await fetch('/agent/scope/query', {
       method: 'POST',
       headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
       body: JSON.stringify({ datasourceId: '<数据源id>', sql: '<SELECT ...>' })
     })
     const body = await res.json()
     // body.data 是行对象数组，字段名和 SQL 列名一致
     ```
   - SQL 必须是 SELECT，最多返回 100 行，单个查询最多运行 10 秒
   - 也可以不调接口，把展示数据直接硬编码在页面里（看用户要求）
4. 体验完整：标题、加载态、空态、错误提示都要有。

## 完成后

把 `create_dashboard` 返回的 `url` **原样**告诉用户，并说明：这是独立页面，点击即可打开；链接里的 token 有效期跟随登录态（7 天），过期后回来重新生成即可。

## 注意

- 创建前先 `list_dashboards` 查重，已有同名看板就 `update_dashboard`
- 修改时先 `get_dashboard(id)` 拿现成 code 再改，不要凭记忆重写，避免覆盖丢内容
- 数据源 id 从 `list_data_sources` 工具获得，不要自己编