# naive-agent-ui 重写计划(Vue3 + Naive UI + UnoCSS)

> 用户要求计划文件落在项目目录:批准后将本计划复制为 `docs/plans/naive-agent-ui-plan.md`。

## 目标

在仓库根目录新建 `naive-agent-ui/`,用 **Vue 3 + Vite + TypeScript + Pinia + Vue Router + Naive UI + Axios + UnoCSS** 重写 agent-ui 的全部功能。**后端接口零改动**;布局/交互/组件用 Naive UI 重新设计,不复制旧手写 CSS 体系。

## 已确认决策

- 主题:Naive UI 原生明/暗两套(darkTheme + themeOverrides 定制品牌色),localStorage 持久化,不搬旧三套皮肤。
- 包管理 pnpm;dev 端口 **5174**;代理规则与 agent-ui 相同(`/agent`、`/auth`、`^/user/(feedback|manage|profile|password)` → 8082)。
- 功能与 agent-ui 全量对齐(见下),不做后端任何改动。

## 功能清单(对齐 agent-ui 现状)

1. **认证**:登录(用户名/邮箱+密码)、登出、`/auth/current`、401 清态回登录、路由守卫(含 `/admin` 管理员守卫)。
2. **会话**:侧栏列表(置顶+日期分组)、搜索(本地过滤)、新建、删除、置顶、切换、占位标题→异步正式标题、窄屏抽屉。
3. **聊天核心**:fetch POST SSE 流式;事件 agent_start/thinking/text_block/tool_call/tool_result/tool_end/agent_result/agent_end;中断停止;失败重试(复用用户气泡);复制;重新生成(重发最后用户消息);消息反馈(有帮助/无帮助,乐观更新)。
4. **渲染**:markdown(marked + highlight.js + DOMPurify)、echarts 围栏块渲染图表、思考过程折叠块、工具调用/结果卡片、图片预览 lightbox。
5. **Agent**:顶栏选择器(名称+模型+描述+能力徽章:思考/工具/MCP/技能/附件)、按用户 id 持久化(默认 flash)、主动切换=新开会话、打开老会话跟随其 agent、无权限空态、能力驱动 UI(技能/附件入口显隐)。
6. **技能**:`/` 唤起技能选择器、`/skill:<name>` 前缀(仅 max)。
7. **附件**:上传、进度、粘贴、多文件、失败提示。
8. **导出**:会话导出 Markdown / HTML / PDF(echarts 先转图片)。
9. **个人中心**:身份卡、编辑资料、修改密码、用量统计(时间维度选择+自定义日期、统计卡含输入/输出 token、柱状图 Tokens/请求/费用三维度、最近请求表点击跳会话)。
10. **管理中心**(仅 admin):Tab 切换——用户(CRUD、禁用踢下线、分配 agents 多选、分页搜索、内置 admin 保护)、意见反馈(状态筛选、标记已处理)、操作日志(搜索、参数截断)。
11. **意见反馈**提交弹窗。
12. **快捷键**:Ctrl/Cmd+K 新对话、Ctrl/Cmd+B 侧栏、Ctrl/Cmd+J 聚焦输入框、Esc 停止生成、帮助面板。

## 技术设计

### 目录结构

```
naive-agent-ui/
├── index.html  package.json  vite.config.ts  uno.config.ts  tsconfig.json
└── src/
    ├── main.ts  App.vue
    ├── api/        http.ts(axios+401拦截) token.ts types.ts auth.ts agent.ts user.ts
    ├── stores/     auth.ts agents.ts chat.ts theme.ts   (pinia)
    ├── sse/        chatStream.ts(逻辑从 agent-ui 照搬)
    ├── utils/      stream.ts format.ts markdown.ts clipboard.ts export.ts exportCharts.ts print.ts usage.ts
    ├── router/     index.ts(登录守卫+admin守卫)
    ├── layouts/    AppLayout.vue(NLayout:侧栏+顶栏+内容,取代旧 data-view hack)
    ├── views/      LoginView.vue ChatView.vue ProfileView.vue AdminView.vue
    ├── components/ SessionList.vue ChatThread.vue AssistantTurn.vue UserBubble.vue
    │               ToolCard.vue ThinkingBlock.vue ChatComposer.vue SkillPicker.vue
    │               AgentSelect.vue ExportMenu.vue ShortcutsDialog.vue FeedbackDialog.vue
    │               admin/UsersPanel.vue admin/FeedbacksPanel.vue admin/LogsPanel.vue
    │               profile/UsagePanel.vue profile/EditProfileDialog.vue profile/PasswordDialog.vue
    └── styles/     naive-theme.ts(themeOverrides) main.css
```

### Naive UI 组件映射(替代手写)

| 旧实现 | 新方案 |
|---|---|
| 手写侧栏/布局/data-view CSS hack | `NLayout + NLayoutSider`(可折叠)+ 正常路由嵌套布局 |
| 账号菜单/Agent 下拉/导出菜单 | `NDropdown` / `NPopover`(Agent 选择器用 NPopover 自定义卡片:名称+模型+描述+`NTag` 能力徽章) |
| 用户/反馈/日志表格+手写分页 | `NDataTable`(自带分页、loading、空态) |
| 新建/编辑用户弹窗+手写校验 | `NModal + NForm + NFormItem` 校验,agents 用 `NCheckboxGroup`,状态用 `NSwitch` |
| window.confirm(删除/禁用) | `NPopconfirm` |
| Tab(管理中心) | `NTabs` |
| 时间维度/筛选下拉 | `NSelect`;自定义日期 `NDatePicker` |
| 状态徽章/Agent 标签 | `NTag` |
| 错误红字/成功提示 | `useMessage()`(NMessageProvider) |
| 加载/空态 | `NSpin` / `NEmpty` |
| 头像 | `NAvatar` |
| 手写 CSS | UnoCSS 原子类 + `styles/naive-theme.ts` 主题覆盖 |

### 保留自研的部分(Naive 不覆盖)

- SSE 流式解析(`sse/chatStream.ts` 照搬,fetch + 手动分帧)。
- 消息回合流式状态机(`utils/stream.ts` 的 createLiveTurn 思路照搬,可按 Naive 风格简化)。
- markdown + echarts 围栏渲染、导出(echarts→图片→md/html/pdf)。
- 会话分组/搜索逻辑、agent 偏好持久化逻辑。

### 交互/体验改进点(相对旧 UI)

- 布局用 NLayout 正经路由嵌套,废弃 body[data-view] CSS hack。
- 表单全部走 NForm 校验,替代手写 required 判断。
- 全局 message/dialog 来自 NMessageProvider/NDialogProvider,替代 window.confirm/alert 与红字。
- 表格统一 NDataTable:分页、加载、空态、排序白拿。
- 聊天体验保持旧版全部行为(流式、暂存后台回合、切会话不断流)。

## 实施阶段(按依赖排序,每阶段可独立验证)

1. **脚手架**:Vite + TS + Pinia + Router + Naive UI + UnoCSS 初始化;proxy;Naive 明暗主题 + themeOverrides;AppLayout;登录页 + 认证闭环(登录/登出/守卫/401)。
2. **聊天骨架**:会话侧栏(列表/分组/搜索/新建/删除/置顶)+ 会话路由 + 消息历史加载。
3. **流式核心**:chatStream 搬迁 + 回合状态机 + 发送/中断/失败重试/markdown 渲染/思考折叠/工具卡片。
4. **Agent 与输入区**:Agent 选择器(能力徽章)、偏好持久化、切换新开;Composer(附件上传/粘贴/图片预览、技能选择器)、能力驱动显隐。
5. **消息增强**:echarts 渲染、复制、反馈、重新生成、导出(md/html/pdf)。
6. **个人中心**:身份卡、编辑资料、修改密码、用量统计(维度切换、最近请求跳会话)。
7. **管理中心**:NTabs + 用户(NDataTable + NModal/NForm CRUD + 禁用 + agents 分配)+ 意见反馈 + 操作日志。
8. **收尾**:快捷键体系+帮助面板、意见反馈弹窗、窄屏响应式、空态打磨。

## 验证

- 每阶段 `pnpm build`(vue-tsc)通过。
- 最终冒烟(需后端环境):登录 → Flash 聊天 → 切 Max 用技能/MCP → 附件上传 → 中断/重试/反馈/导出 → 个人中心改资料/密码、用量维度切换 → 管理中心三 Tab CRUD → 明暗主题切换 → 快捷键。
- agent-ui 保持不动,两个前端可并行跑(5173/5174)。
