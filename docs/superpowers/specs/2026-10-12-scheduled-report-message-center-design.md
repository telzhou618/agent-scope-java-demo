# 定时任务 + 消息中心 设计文档

日期：2026-10-12（v2：调度机制改为 Quartz 持久化）
范围：`agent-app`（后端）+ `naive-agent-ui`（前端）。`agent-ui` 不在本期范围内。

## 1. 需求概述

1. 用户在对话中说「请每周一把上周的运营报告发我」，Agent 调用工具自动创建定时任务。
2. 到点后调度器触发任务：用创建任务时的 Agent 档位在后台跑一次完整 Agent 会话（可调 MySQL 查询、导出等工具），产出 Markdown 报告。
3. 报告写入消息中心；另有一种系统消息：管理员把用户的意见反馈标记为「已处理」时，给该用户发系统通知。
4. 消息中心：消息列表（已读/未读、头像、发送方名称）、详情页（Markdown 渲染，与对话消息渲染一致）。
5. 顶栏右侧加消息中心铃铛图标（含未读角标），点击进消息列表。
6. 定时任务管理为独立页面：侧栏账号菜单新增「定时任务」菜单项（位于「个人中心」下方），列表 + 启停 + 删除。

已确认的关键决策：

- 调度机制：**`spring-boot-starter-quartz`，MySQL JobStore（`JobStoreTX`）持久化**。触发器状态由 Quartz 系统表（`QRTZ_*`）承载，应用重启自动恢复，无需自建扫描器。
- 任务执行：**用创建时的档位跑独立后台会话**；后台会话不写 `session_meta`，因此天然不出现在侧栏会话列表（列表只显示有 meta 的会话）。
- 任务管理：**界面手动管理**（列表/启停/删除）；Agent 工具只提供创建与查询，**不提供删除工具**（不需要框架的人工确认链路）。
- 消息中心：**通用结构**（`type` 字段），本期接入两种来源：`report`（定时任务报告）、`system`（系统通知）。
- 前端只实现 `naive-agent-ui`；原型已确认：`naive-agent-ui/原型/message-center.html`。

## 2. 后端设计（agent-app）

### 2.1 数据表

本期新增的表全部放在独立脚本 **`sql/schedule.sql`**（不改动 `sql/init.sql`），包含三部分：Quartz 官方 MySQL InnoDB DDL（11 张 `QRTZ_*` 表，单实例不启用集群 `org.quartz.jobStore.isClustered=false`）、业务表 `t_scheduled_task`、消息表 `t_message`。

**业务表**（任务的业务属性；触发状态由 Quartz 表承载，不在业务表重复）：

```sql
CREATE TABLE t_scheduled_task (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT       NOT NULL,                -- 创建人（DB 自增 id）
  agent_name  VARCHAR(32)  NOT NULL,                -- 创建时档位 flash/plus/max
  description VARCHAR(500) NOT NULL,                -- 需求原文
  cron        VARCHAR(64)  NOT NULL,                -- Quartz cron（6 段，含秒）
  status      TINYINT      NOT NULL DEFAULT 1,      -- 1 启用 / 0 停用（与 trigger 暂停状态同步维护）
  last_run_at DATETIME     NULL,
  create_time DATETIME     NOT NULL,
  update_time DATETIME     NOT NULL,
  KEY idx_user (user_id)
);

CREATE TABLE t_message (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT       NOT NULL,
  type        VARCHAR(16)  NOT NULL,                -- report / system
  sender      VARCHAR(32)  NOT NULL DEFAULT '',     -- Agent 名（flash/plus/max）；系统消息为空串
  title       VARCHAR(200) NOT NULL,
  content     MEDIUMTEXT   NOT NULL,                -- Markdown
  is_read     TINYINT      NOT NULL DEFAULT 0,      -- 0 未读 / 1 已读
  task_id     BIGINT       NULL,                    -- 来源任务，系统消息为 NULL
  run_status  VARCHAR(16)  NOT NULL DEFAULT 'success',  -- success / failed（系统消息恒 success）
  create_time DATETIME     NOT NULL,
  KEY idx_user_read (user_id, is_read),
  KEY idx_user_time (user_id, create_time)
);
```

实体、Mapper（MyBatis-Plus `BaseMapper`，无需自定义 XML）、VO 按现有 `entity/mapper/vo` 分包惯例新增；`QRTZ_*` 表不建实体，只由 Quartz 使用。

### 2.2 Quartz 配置

- `agent-app/pom.xml` 新增 `spring-boot-starter-quartz`。
- `application.yaml`：

```yaml
spring:
  quartz:
    job-store-type: jdbc
    jdbc:
      initialize-schema: never        # 表结构由 sql/schedule.sql 提供
    properties:
      org.quartz.jobStore.isClustered: false
      org.quartz.scheduler.instanceName: agent-demo
```

- Job 内注入 Spring Bean：注册自定义 `SpringBeanJobFactory`（`AutowireCapableBeanFactory` 注入）到 `SchedulerFactoryBean`，使 `ScheduledTaskJob` 可 `@Autowired` 现有 Service。
- 时区固定 `Asia/Shanghai`（CronTrigger `inTimeZone`）。

### 2.3 Agent 工具 `ScheduleTools`

`com.example.agent.tools.ScheduleTools`（`@Service`，风格对齐 `MysqlQueryTools`）：

- `create_schedule(description, cron)`：
  - 用 Quartz `CronExpression.isValidExpression` 校验 cron，非法直接返回错误提示（LLM 可修正重试）；
  - 落库 `t_scheduled_task`，然后向 `Scheduler` 注册：`JobDetail`（durable，key = `task-<id>`，JobDataMap 带 taskId）+ `CronTrigger`（misfire 策略 `MISFIRE_INSTRUCTION_DO_NOTHING`，即错过后等下个周期、不补跑）；
  - 返回任务 id 与下次执行时间（`trigger.getNextFireTime()`）。
  - 用户身份：从工具执行的 `RuntimeContext` 取 `userId`（与 `TokenUsageMiddleware` 同一来源）。
- `list_schedules()`：返回当前用户的任务列表（id、描述、cron、状态、上次/下次执行时间），供 Agent 回答「我有哪些定时任务」。

注册：三个档位的 `registerLocalTools` 均加入（与 `MysqlQueryTools` 一致，flash/plus/max 都能建任务）。

### 2.4 执行 Job `ScheduledTaskJob extends QuartzJobBean`

每次触发（JobDataMap 取 taskId）：

1. 读 `t_scheduled_task`；任务已删除则 `scheduler.deleteJob` 自清；创建用户不存在或已禁用（`t_user.status = 0`）→ 记日志，不执行。
2. 构建 `RuntimeContext(userId, sessionId = "task-<taskId>-<yyyyMMddHHmmss>")`，调 `agentRegistry.getAgent(agentName).streamEvents(userMessage, context)` 跑完；用户消息 = 任务描述原文。
3. 从事件流取最终正文（`AgentResultEvent` 内容；兜底取最后文本块）。
4. 成功 → 写 `t_message`（`type=report`、`sender=agent_name`、`title=描述 + 执行日期`、`run_status=success`）。
5. 异常或超过 5 分钟（`orTimeout`）→ 写 `run_status=failed` 的消息，内容为失败原因与「将于下个周期重试」说明。
6. 更新 `last_run_at = now`。下次执行时间由 Quartz 触发器自行维护，业务表不存。

### 2.5 系统消息钩子

`UserFeedbackService.updateStatus`：当反馈状态被改为「已处理」时，向该反馈的 `user_id` 写 `t_message`（`type=system`、`sender=''`、标题「你提交的意见反馈已处理」、正文含反馈原文引用）。只在状态**变为**已处理时触发，重复设置不重复发。

### 2.6 REST API

新增 `MessageController`（`/agent/scope/message`）与 `ScheduleController`（`/agent/scope/schedule`），身份从 token 解析，风格对齐现有 Controller（薄层 + Service）：

| 接口 | 说明 |
| --- | --- |
| `GET /message/page?page&size` | 消息分页（倒序），返回含 `sender`（前端据此取 Agent 头像与显示名） |
| `GET /message/unreadCount` | 未读数（顶栏角标） |
| `GET /message/detail?id=` | 消息详情 |
| `POST /message/read` | 标记已读 `{id}` |
| `GET /schedule/list` | 当前用户任务列表；下次执行时间从 Quartz trigger 实时读取 |
| `POST /schedule/toggle` | 启用/停用 `{id, status}`：更新业务表 + `scheduler.pauseTrigger/resumeTrigger`；恢复时 Quartz 按 cron 自动计算下一周期（不补跑） |
| `POST /schedule/delete` | 删除 `{id}`：`scheduler.deleteJob` + 删业务行 |

均加 `@OperationLog`（写操作）与参数校验；只能操作本人的任务/消息。

## 3. 前端设计（naive-agent-ui）

以原型 `naive-agent-ui/原型/message-center.html` 为准。

### 3.1 入口与未读角标

- 对话页顶栏右侧加铃铛 `NIcon` 按钮 + `NBadge` 未读数（为 0 不显示角标），点击 `router.push('/messages')`。
- 未读数获取：登录后/回到对话页拉一次 `unreadCount`，之后每 30s 轮询；从消息中心返回时刷新。

### 3.2 消息中心 `/messages`

- 列表行：头像（`sender` 为空 → 系统信封图标头像；否则按 `sender` 从 agents store 取该档位头像与显示名，取不到时退化为首字母渐变圆）、发送方名称、标题（未读加粗 + 红点）、内容预览、时间。
- 服务端分页（复用现有 `remote + pagination` 模式，每页 10 条）。
- 点击行：先 `POST /message/read` 标已读，再跳 `/messages/:id`。

### 3.3 消息详情 `/messages/:id`

- 头部：头像 + 标题 + 发送方 + 时间。
- 正文：Markdown 渲染，**与对话消息同一套渲染管线**——把 `AssistantTurn` 里的 Markdown 渲染抽成共享组件（如 `MarkdownView.vue`），对话气泡与消息详情共用，报告中的代码块/表格/echarts 图表块表现一致；失败消息展示失败原因。
- 报告中的导出文件链接为签名链接（免登录可下载），直接可点。

### 3.4 定时任务页 `/schedules`

- 入口：侧栏账号菜单「个人中心」下方新增「定时任务」（时钟图标），点击跳转。
- 列表：描述、cron（等宽字体）、档位显示名、状态、上次/下次执行时间；操作：启停 `NSwitch`、删除（`NPopconfirm` 二次确认）。
- 页底提示：任务通过对话创建。

### 3.5 路由与权限

- 新增路由 `/messages`、`/messages/:id`、`/schedules`，均要求登录（沿用现有路由守卫）。
- Vite 代理无需改动（接口在 `/agent/scope/**` 下）。

## 4. 错误处理与边界

| 场景 | 行为 |
| --- | --- |
| cron 非法 | `create_schedule` 返回错误文本，LLM 修正或告知用户 |
| 执行超时（5 分钟） | 写 `run_status=failed` 消息，任务进入下周期 |
| 执行抛异常 | 同上，内容含异常摘要 |
| 用户被禁用/删除 | 跳过执行，记日志 |
| 应用重启 | Quartz JobStore 恢复全部 trigger；misfire 策略 `DO_NOTHING`，错过的周期不补跑 |
| 停用后再启用 | `resumeTrigger` 后按 cron 计算下一周期（不补跑停用期间的触发） |
| 操作他人任务/消息 | Service 层按 `user_id` 过滤/校验，返回业务异常 |

## 5. 验证

- 后端：`mvn -pl agent-app compile`；接口经 Knife4j 手动验证（建任务 → 直接用 Quartz cron 把触发时间调到近一分钟内，如每分钟触发 → 产生消息 → 列表/详情/已读/未读数全链路；重启应用验证任务不丢）。
- 前端：`vue-tsc --noEmit`；手动走查：对话建任务 → 铃铛角标 → 列表已读/未读 → 详情 Markdown 渲染 → 任务启停/删除。
- 系统消息：管理后台把一条意见反馈标为已处理 → 对应用户消息中心出现系统通知。

## 6. 明确不做（YAGNI）

- 不做 agent-ui 端的对应界面。
- 不做 Agent 侧删除/修改任务的工具，不做工具调用人工确认链路。
- 不做 Quartz 集群、任务执行历史列表、消息实时推送（WebSocket），角标轮询即可。
- 不做消息删除、消息类型筛选。
