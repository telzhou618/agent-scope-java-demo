# 插件功能设计文档

> 日期：2026-10-15
> 状态：待评审（已按评审意见修订两轮）

## 1. 目标

在聊天输入框支持 `/` 插件：

- 输入框**开头**输入 `/` 唤起插件选择列表，插件**单选**；
- 选中的插件通过聊天请求的**独立字段**传入后端，后端**优先处理插件**；
- 插件以策略模式实现，可新增实现类扩展；
- 前端用**区别于技能/数据源的标签**展示插件（含小图标、可整体删除、固定在首行开头、后接冒号与各插件专属提示语）；
- 历史消息中展示**插件标签 + 用户原始参数**（不展示改写后的内部文案）。

## 2. 已确认决策

| 决策点 | 选择 |
| --- | --- |
| 插件执行模型 | **统一改写提示词后发给 LLM**；插件可在改写前调用 Service 取数据并注入提示词 |
| 历史显示 | **插件标签 + 用户输入的原始参数**，不显示改写后的文案（需要把原始参数额外存到 metadata） |
| `@` 技能/数据源 | **后端不做解析改写，原样透传给 Agent** 自行判断；插件参数里同样可继续 `@` 选择技能/数据源 |
| 示例插件 | 创建定时任务（改写）、查询本月额度（先取数据再改写 + 写死展示口径）、导出 CSV（改写） |
| 注册/开关 | Java `Plugin` 接口 + 实现类（Spring 自动收集），`application.yaml` 用 `agent.plugins.<id>.enabled` 控制开关 |
| 权限 | 不做权限，登录用户均可使用 |

## 3. 总体架构

```
前端聊天框
  ├─ "/" 在开头输入 → 插件下拉（单一选择）
  ├─ 选中后首行展示 [🔌 插件名]：参数输入区（placeholder 来自插件）——标签仅是 UI
  ├─ 参数内可继续 @ 选择技能/数据源（原样透传，不解析）
  └─ 发送：plugin=插件id（独立字段），message=参数文本（不含插件 token）

后端
  ├─ /agent/scope/plugins  返回插件元信息（id/name/description/placeholder）
  ├─ AgentChatRequest.plugin  插件 id（可选）
  └─ AgentChatService.doChatSse
       ├─ 有 plugin：PluginRegistry 查找 → process(params) 改写消息
       │             → 改写后的消息喂给 LLM（自动落库）
       │             → 消息 metadata 记录 { plugin, pluginArgs=原始参数 } 供历史回显
       └─ 无 plugin：走原聊天流
  └─ @ 技能/数据源：不做后端解析改写，随消息原样进入 LLM
```

## 4. 后端设计

### 4.1 包结构与接口（策略模式）

新增包 `com.example.agent.plugin`：

```java
public interface Plugin {
    String id();            // 唯一标识，前端 /plugins 返回，聊天字段 plugin 使用
    String name();          // 展示名
    String description();   // 描述（下拉列表展示）
    String placeholder();   // 冒号后的参数提示语
    boolean enabled();      // 由 yml 开关注入

    /** 改写用户消息；可在内部调用任意 Service，把查到的数据拼入提示词 */
    String process(PluginContext context);
}
```

- `PluginContext`：`{ userId, agentName, message(参数文本，可含 @ 技能/数据源), attachments }`
- `PluginRegistry`：注入 `List<Plugin>`（Spring 自动收集所有实现类），过滤 `enabled()`，构建 `id -> Plugin` Map；提供 `get(id)`、`list()`。

### 4.2 yml 开关

```yaml
agent:
  plugins:
    schedule:
      enabled: ${PLUGIN_SCHEDULE_ENABLED:true}
    monthly-quota:
      enabled: ${PLUGIN_MONTHLY_QUOTA_ENABLED:true}
    export-csv:
      enabled: ${PLUGIN_EXPORT_CSV_ENABLED:true}
```

每个实现类用 `@Value("${agent.plugins.<id>.enabled:true}")` 注入 `enabled()`。

### 4.3 聊天接口

`AgentChatRequest` 新增字段：

```java
@Schema(description = "插件 id，命中时优先执行插件")
private String plugin;
```

`AgentChatService.doChatSse` 消息构建：

1. `plugin` 非空 → `pluginRegistry.get(plugin)`，未找到返回业务异常。
2. `String rewritten = plugin.process(context)`（`context.message` 为原始参数）。
3. 构建 `UserMessage`：
   - **正文 text = rewritten**（喂给 LLM，也随 Agent 流自动落库）。
   - **metadata**：`plugin` = 插件 id、`pluginArgs` = 用户原始参数（供历史回显，LLM 不感知 metadata）。
4. `plugin` 为空 → 走原聊天流。

`@` 技能/数据源不做后端改写（`applyMentions` 保持透传/移除解析），随消息原样进入 Agent。

### 4.4 历史存储

- `UserMessage` 的 metadata 携带 `plugin` 与 `pluginArgs`，随 Agent 状态落库。
- `SessionManageService.listMessages` 返回的 `Msg` 天然含 metadata，前端据此还原「插件标签 + 原始参数」。
- 助手回复由 Agent 自动落库，无需额外持久化。

### 4.5 `/plugins` 接口

`AgentScopeController` 新增：

- `GET /agent/scope/plugins` → `Result<List<PluginVO>>`
- `PluginVO { id, name, description, placeholder }`（不含 enabled/内部实现）

### 4.6 示例插件

1. **创建定时任务 `schedule`**
   - placeholder：`请输入定期要做的事和执行时机`
   - process：返回 `请使用定时任务工具（create_schedule）完成以下需求：<参数>`

2. **查询本月额度 `monthly-quota`**
   - placeholder：`查询我本月各档位的用量与剩余额度`
   - process：先调用 `TokenQuotaService`/用量服务取当前用户本月各档位已用与剩余额度，再把数据注入提示词，**写死展示口径**（如“按 Agent 档位列出已用/剩余/总量，用表格呈现”），并注明**忽略用户输入的其他内容**，返回改写后的消息。

3. **导出 CSV `export-csv`**
   - placeholder：`请输入要导出的数据或查询条件`
   - process：返回 `请使用数据导出工具（export_sql_to_csv）完成以下需求，必要时先 list_data_sources 选择数据源：<参数>`

## 5. 前端设计（仅 naive-agent-ui）

### 5.1 API / 类型

- `api/types.ts`：新增 `PluginInfo { id, name, description, placeholder }`；`ChatRequest` 新增 `plugin?: string`。
- `api/agent.ts`：新增 `getPlugins()` → `GET /agent/scope/plugins`。

### 5.2 插件 store

`stores/plugins.ts`：拉取并缓存插件列表，提供 `list`、`byId`（供输入框下拉与历史标签解析名称/图标）。

### 5.3 输入框

复用并扩展 `MentionEditor.vue`：

- 当光标在**第 0 位置**且输入 `/` 时，弹出插件下拉（单分组、单选）。
- 选择后：在首行开头插入一个 `contenteditable=false` 的**插件标签**（通用插件图标 + 插件名，样式区别于技能/数据源标签），后接固定 `：`，参数区显示该插件 `placeholder`（空参数时）。
- 插件标签可整体删除（×，退格到开头整体删除）；插件只允许一个，删除后回到普通文本模式。
- **序列化**：插件标签与冒号是 UI 概念，**不进入** `message`；`message`＝参数文本，插件 id 通过 `v-model:plugin` 提交给 composer。
- 参数区仍支持 `@` 技能/数据源（沿用现有 @ 提词，原样透传）。

### 5.4 状态/发送

- `useComposer`：新增 `pluginId` ref 与 `setPlugin/clearPlugin`。
- `ChatComposer.submit`：`chat.send(text, attachments, { plugin: pluginId })`；无插件不传。
- `stores/chat.ts` `send()`：`SendOptions` 新增 `plugin?`，写入 `UserTurn.plugin` 与 `chatStream` 载荷（`ChatRequest.plugin`）。
- `utils/model.ts` `UserTurn` 新增 `plugin?: string`。

### 5.5 历史渲染

- `utils/history.ts` `toTurns`：读到 `message.metadata.plugin` 时，把 `UserTurn.plugin = metadata.plugin`、`UserTurn.text = metadata.pluginArgs`（**忽略消息正文里的改写文本**）；无插件时走现有正文解析。
- `UserBubble.vue`：`turn.plugin` 存在时，在正文前渲染插件标签（通用插件图标 + 插件名，查 `plugins.byId`），正文为 `turn.text`（原始参数）；样式与输入框标签一致、只读不带 ×。

## 6. 数据流（统一改写模型 + 原始参数回显）

```
用户: [🔌创建定时任务]：每天8点查数据库
前端 → POST /chat_sse { plugin:"schedule", message:"每天8点查数据库" }
后端 → process({message:"每天8点查数据库"}) → "请使用定时任务工具完成：每天8点查数据库"
     → UserMessage(text=改写文本, metadata={plugin:"schedule", pluginArgs:"每天8点查数据库"})
     → 喂给 LLM 并随 Agent 流落库
历史 → metadata → UserBubble 渲染 [🔌创建定时任务]：每天8点查数据库
```

```
用户: [🔌查询本月额度]：
前端 → POST /chat_sse { plugin:"monthly-quota", message:"" }
后端 → process(...) 先查额度数据 → 改写（含数据 + 写死展示口径）
     → UserMessage(text=改写文本, metadata={plugin:"monthly-quota", pluginArgs:""})
     → 喂给 LLM 并落库
历史 → metadata → UserBubble 渲染 [🔌查询本月额度]：（原始参数为空）
```

## 7. 风险与待确认

1. **首行标签的 contenteditable 细节**：插件标签 + 固定冒号 + 参数多行文本在 contenteditable 中的光标与退格边界需要精细处理。
2. **`@` 技能/数据源透传后的 LLM 理解**：不解析改写后，依赖 Agent 自行把 `@` 标记与工具对应；插件改写提示词里需有清晰引导（如导出 CSV 提示先选数据源）。
3. **metadata 键命名**：`plugin` / `pluginArgs` 作为会话消息 metadata 的固定键，需与历史解析、分享页（ShareController 透传 metadata）保持一致。

## 8. 验证

- 后端：`mvn -q -pl agent-app -am compile -DskipTests`
- 前端：`cd naive-agent-ui && pnpm exec vue-tsc --noEmit`
- 端到端：`/` 唤起插件 → 选「创建定时任务」输入参数（含 @）发送 → 校验前端标签、后端改写、history 回显「标签 + 原始参数」；「查询本月额度」校验先取数再改写、展示符合写死口径。