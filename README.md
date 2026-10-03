# agent-scope-java-demo

Agent + MCP 演示工程：基于 [AgentScope Java](https://github.com/agentscope-ai/agentscope-java) Harness 构建智能体服务，通过 MCP（Streamable HTTP）调用工具服务。

![img.png](imgs/img.png)

## 模块说明

| 模块 | 端口 | 说明 |
| --- | --- | --- |
| `mcp-server` | 8081 | 基于 Spring AI MCP Server（STREAMABLE 协议），提供示例工具：当前时间、模拟天气查询 |
| `agent-app` | 8082 | 基于 AgentScope HarnessAgent 的智能体服务，连接 MCP server 获取工具，对外提供 SSE 流式对话接口；含 Sa-Token + MyBatis-Plus 的用户登录（MySQL 存用户、Redis 存 token） |
| `agent-ui` | 5173 | 基于 Vite + Vue 3 的对话界面（登录页 + Vue Router + Pinia + Axios），对接 agent-app；原型见 `agent-ui/原型/index.html` |

## 环境要求

- JDK 17+
- Maven 3.6+
- DashScope API Key（环境变量 `YOKA_DASHSCOPE_API_KEY`）
- MySQL 8（`localhost:3306`，初始化脚本见 `sql/init.sql`）
- Redis（`localhost:6379`，存登录 token）

## 启动方式

```bash
# 1. 初始化数据库（建库 agent_demo + 用户表 t_user + 种子用户 admin/admin123）
mysql -h127.0.0.1 -uroot -p < sql/init.sql

# 2. 编译
mvn clean compile

# 3. 先启动 MCP server（8081）
mvn -pl mcp-server spring-boot:run

# 4. 再启动 Agent 服务（8082）
export YOKA_DASHSCOPE_API_KEY=sk-xxxx
mvn -pl agent-app spring-boot:run

# 5. 启动前端界面（5173，需要先启动 agent-app）
cd agent-ui
pnpm install
pnpm dev
```

浏览器打开 `http://localhost:5173` 会跳到登录页，用种子账号 **admin / admin123**（或邮箱 `admin@example.com`）登录后进入对话。除 `/auth/login`、`/auth/logout` 外，所有接口都要带 `Authorization: Bearer <token>`；Agent 接口的用户身份由后端从 token 解析（不再传 userId），会话按用户隔离。前端通过 Vite 代理把 `/agent/**`、`/auth/**` 转发到 `http://localhost:8082`，无需处理跨域。

路由：

| 路径 | 说明 |
| --- | --- |
| `/login` | 登录页 |
| `/chat` | 新对话；发首条消息后地址变为 `/chat/<uuid>` |
| `/chat/:sessionId` | 指定会话（sessionId 为 uuid） |
| `/profile` | 个人主页（真实用户信息来自 `/auth/current`） |

界面上支持的接口：

| 界面能力 | 接口 |
| --- | --- |
| 登录、退出、当前用户 | `POST /auth/login`、`POST /auth/logout`、`GET /auth/current` |
| 侧栏会话列表、相对时间分组 | `GET /agent/scope/getSessions` |
| 新建会话（占位标题 + 异步生成标题） | `POST /agent/scope/createSession` |
| 打开会话、渲染历史消息（思考过程/工具调用/正文） | `GET /agent/scope/getMessages` |
| 发送消息、流式渲染 | `POST /agent/scope/chat_sse` |
| 停止生成（输入框右侧方块按钮） | `GET /agent/scope/interrupt` |
| 会话项悬停后的删除按钮 | `GET /agent/scope/delSession` |
| 会话项悬停后的置顶/取消置顶按钮 | `GET /agent/scope/pinSession` |
| 输入框输入 `/` 唤出技能列表 | `GET /agent/scope/skills` |

说明：

- 登录状态由 Pinia 管理，token 存 `localStorage` 的 `agent-ui:token`（后端存 Redis，有效期 7 天）；除登录/退出外的接口由前端 Axios 拦截器自动携带 token，401 时自动回登录页。
- 新建会话在发出第一条消息时才生成 `sessionId`（uuid），前端先调用 `POST /agent/scope/createSession`：后端立即写入「新会话」占位标题并返回，侧栏马上可见；随后异步调用模型根据首条消息生成正式标题并更新数据库，首轮 AI 回答结束后前端刷新会话列表即可看到新标题。
- 个人主页的用户信息（昵称/邮箱/头像）来自 `/auth/current`；用量/费用/图表仍是**演示数据**（后端暂无对应接口），页面上已标注。
- 附件支持按钮选择和直接粘贴（图片/文档/文本类，单个 10MB 以内）；选择工具、模型切换为占位控件（禁用状态）；顶栏 ⋯ 菜单可把**当前会话的对话正文导出为 Markdown / HTML / PDF**（三者内容一致，均不含思考过程与工具调用；流式中或空会话时该项置灰）。PDF 走浏览器打印，会弹出系统打印对话框，在对话框里选「另存为 PDF」。
- 技能快捷指令：输入框输入 `/` 唤出已安装技能列表（`/skill:名称` + 描述，支持模糊过滤、↑↓ 选择、Enter/Tab 确认、ESC 关闭）；技能定义在 `agent-app/src/main/resources/skills/<name>/SKILL.md`（YAML frontmatter 写 name/description，正文为技能指令），消息以 `/skill:<name>` 开头时后端把技能指令注入用户消息再交给 Agent。新增技能需重启 agent-app。
- 侧栏左下角头像点开是菜单（个人主页 / 退出），「退出」调用 `/auth/logout` 后回到登录页；侧栏头部按钮可收起侧栏（窄屏关抽屉，宽屏折叠整列，顶栏汉堡按钮展开）。
- 流式过程中后端通过 `tool_end` 事件返回工具结果状态（`success/error/interrupted/denied`），工具结果返回成功即标记「成功」，其余状态标记「失败」，无需等待整轮回答结束；历史消息里同样依据 `state` 字段还原。

可选环境变量：

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `YOKA_DASHSCOPE_API_KEY` | 无（必填） | DashScope API Key |
| `MYSQL_PASSWORD` | `root` | MySQL 密码（本机演示默认值，生产请覆盖） |
| `MCP_SERVER_TOKEN` | 空 | MCP server 访问令牌，为空时不附加 Authorization 头 |

## 接口示例

先登录拿 token，再调 Agent 接口：

```bash
# 登录（账号 admin/admin123，密码 BCrypt 加密存储，生成工具见 PasswordGeneratorTest）
curl -X POST http://localhost:8082/auth/login \
  -H "Content-Type: application/json" \
  -d '{"account":"admin","password":"admin123"}'

# 流式对话（SSE），用户身份由 token 解析
curl -N -X POST http://localhost:8082/agent/scope/chat_sse \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <上一步返回的 token>" \
  -d '{"sessionId":"11","message":"查询一下杭州今天的天气"}'
```
返回结果:
``` text
data:{"content":"","type":"agent_start"}

data:{"content":"The","type":"thinking"}

data:{"content":" user wants to check","type":"thinking"}

data:{"content":" the weather in Hang","type":"thinking"}

data:{"content":"zhou today","type":"thinking"}

data:{"content":". Let me first","type":"thinking"}

data:{"content":" get the","type":"thinking"}

data:{"content":" current date/time and","type":"thinking"}

data:{"content":" then query the weather","type":"thinking"}

data:{"content":" for Hangzhou.","type":"thinking"}

data:{"toolCall":{"toolCallId":"call_fff3b2bcfc62485485078ba0","toolName":"get_current_datetime","toolParams":"{}"},"type":"tool_call"}

data:{"toolCall":{"toolCallId":"call_fe8265ffb6554f449ea452c8","toolName":"queryWeather","toolParams":"{\"city\": \"杭州\"}"},"type":"tool_call"}

data:{"toolCall":{"toolCallId":"call_fff3b2bcfc62485485078ba0","toolName":"get_current_datetime","toolResults":"2026-07-22 17:40:07"},"type":"tool_result"}

data:{"toolCall":{"toolCallId":"call_fe8265ffb6554f449ea452c8","toolName":"queryWeather","toolResults":"城市杭州天气信息，温度为25度，湿度为60%，天气状况为晴天。"},"type":"tool_result"}

data:{"content":"The","type":"thinking"}

data:{"content":" user asked","type":"thinking"}

data:{"content":" about the weather","type":"thinking"}

data:{"content":" in Hangzhou today","type":"thinking"}

data:{"content":". I have the","type":"thinking"}

data:{"content":" results now.\n","type":"thinking"}

data:{"content":"以下是杭州今天（","type":"text_block"}

data:{"content":"202","type":"text_block"}

data:{"content":"6年7月","type":"text_block"}

data:{"content":"22日","type":"text_block"}

data:{"content":"）的天气信息：","type":"text_block"}

data:{"content":"\n\n|","type":"text_block"}

data:{"content":" 项目 | ","type":"text_block"}

data:{"content":"详情","type":"text_block"}

data:{"content":" |\n|------","type":"text_block"}

data:{"content":"|------|\n","type":"text_block"}

data:{"content":"| 🌡","type":"text_block"}

data:{"content":"️ 温度 |","type":"text_block"}

data:{"content":" **","type":"text_block"}

data:{"content":"25°C**","type":"text_block"}

data:{"content":" |\n|","type":"text_block"}

data:{"content":" 💧 湿度 |","type":"text_block"}

data:{"content":" **60%**","type":"text_block"}

data:{"content":" |\n|","type":"text_block"}

data:{"content":" ☀️ 天气","type":"text_block"}

data:{"content":"状况 | **","type":"text_block"}

data:{"content":"晴天** |\n\n","type":"text_block"}

data:{"content":"今天杭州天气晴朗","type":"text_block"}

data:{"content":"，温度","type":"text_block"}

data:{"content":"适宜，非常适合外出","type":"text_block"}

data:{"content":"活动！😊","type":"text_block"}

data:{"content":"[io.agentscope.core.message.ThinkingBlock@12ed8199, 以下是杭州今天（2026年7月22日）的天气信息：\n\n| 项目 | 详情 |\n|------|------|\n| 🌡️ 温度 | **25°C** |\n| 💧 湿度 | **60%** |\n| ☀️ 天气状况 | **晴天** |\n\n今天杭州天气晴朗，温度适宜，非常适合外出活动！😊]","type":"agent_result"}

data:{"content":"","type":"agent_end"}

```

历史消息：`GET /agent/scope/getMessages?sessionId=s1`（带 token）

会话列表：`GET /agent/scope/getSessions`（带 token）

中断会话：`GET /agent/scope/interrupt?sessionId=s1`（带 token）

API 文档：启动 agent-app 后访问 `http://localhost:8082/doc.html`（Knife4j）。

## 说明

- 用户存储：MySQL `agent_demo.t_user`（MyBatis-Plus），密码 BCrypt 加密；登录态用 Sa-Token 存 Redis（有效期 7 天），SQL 脚本在 `sql/` 目录。
- 智能体状态与上下文默认存储在 `~/.agentscope` 目录下（JsonFileAgentStateStore），按登录用户的 DB 自增 id 分目录隔离。
- `AgentConfig` 中工具权限模式为 `PermissionMode.BYPASS`（不校验权限），仅供演示，生产环境请勿使用。
