# 会话标题 AI 生成 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新会话首次发消息时由 qwen3.6-flash 根据首条用户消息生成会话标题（AI 总结，非截断），一次落库、SSE 推送 title 事件，侧栏即时更新；getSessions 优先读库中标题。

**Architecture:** chatSse 入口读自定义 state_key `session_meta` 判定是否已有标题；无则用独立 flash 模型生成（与主回复并行、不阻塞），写入 agentStateStore 后以 `type=title` 的 SSE 事件并入当前流；getSessions 优先读 meta.title，缺省回退现有截断逻辑。标题调用独立于 harnessAgent 链，token 不计入统计。

**Tech Stack:** Spring Boot 4.x（Java 17）、AgentScope 2.0.0（core + extensions-mysql + dashscope）、MyBatis-Plus、Vue3 + TS + Pinia + Vue Router + Axios

**Spec:** 无独立 spec 文档，本会话"探讨"轮结论即规格。三处定稿决策（默认执行，如需改动请说明）：
1. 存储 = 自定义 state_key `session_meta`（SessionMeta POJO，复用 agentscope_sessions 表，不建新表）。
2. 侧栏更新 = SSE `type=title` 事件（不等列表刷新）。
3. 标题生成的 token 不计入个人中心用量统计。

## Global Constraints

- agentscope.version = 2.0.0，core/mysql/dashscope 同版本，不升级。
- 不修改 agentscope_sessions 表结构（init.sql 注释明确"与官方组件内置 SQL 严格一致，请勿修改"）。
- 标题调用不经过 harnessAgent / TokenUsageMiddleware / usageService.record。
- 包结构沿用 controller / service / mapper / entity / dto / vo / config。
- 构建命令必带 `-Dmaven.repo.local=D:\repo`；IDEA 中 parent 4.1.0 解析失败为既有环境问题，与本功能无关。
- 新类型 SSE 事件沿用既有"新事件新分支"机制；title 是元数据，不在消息区渲染新条目。

## Review Focus

- 并发/重复触发：同一会话重发消息 → 按"meta 有无标题"判定只生成落库一次（表 PK 唯一性兜底）。
- flash 失败/超时：主对话流不受任何影响，回退截断标题并落 meta，SSE 正常结束。
- 前端 done 后即关连接：尾随 concat 的 title 事件会被吞 —— 默认采用 mergeWith 并发插流，评审时确认事件能在 10 秒窗口内到达。
- JDBC 阻塞事件循环：meta 读/写必须 `subscribeOn(boundedElastic)`。
- 存量会话：无 meta 行时读取回退旧截断逻辑，行为与现状一致。

---

### Task 1: dto — SessionMeta

**Files:**
- Create: `agent-app/src/main/java/com/example/agent/dto/SessionMeta.java`

**Interfaces:**
- Produces: `class SessionMeta implements State`（FQCN 动工前解包核实，预计 `io.agentscope.core.state.State`），仅字段 `title` + getter/setter，JSON 序列化由 MysqlAgentStateStore 完成。

- [x] Step 1: 解包 `/d/repo/io/agentscope/agentscope-core/2.0.0/` sources 确认 State 接口 FQCN
- [x] Step 2: 实现 SessionMeta POJO
- [x] Step 3: 构建验证：`mvn -Dmaven.repo.local=D:\repo -DskipTests -pl agent-app -am package -f pom.xml`

### Task 2: config — 抽公共 flash 模型构建

**Files:**
- Modify: `agent-app/src/main/java/com/example/agent/config/AgentConfig.java:89-94` 一带

**Interfaces:**
- Produces: 私有方法 `buildFlashModel()`（qwen3.6-flash，与 compaction 共用样板）+ `@Bean titleModel`，供 Task 3 注入。

- [x] Step 1: 抽取 `buildFlashModel()`，compactionModel 改经其构建
- [x] Step 2: 新增 titleModel bean
- [x] Step 3: 编译通过；启动冒烟确认 compaction 行为不回归

### Task 3: service — SessionTitleService

**Files:**
- Create: `agent-app/src/main/java/com/example/agent/service/SessionTitleService.java`

**Interfaces:**
- Consumes: AgentStateStore（AgentConfig.agentStateStore() 同一实例）、titleModel
- Produces: `Mono<String> generateIfAbsent(String userId, String sessionId, String firstUserText)` —— 已有标题返回空 Mono；否则 flash 生成（10s 超时、blockLast 收敛）→ 异常回退 firstUserText 截断 20 字 → save meta → 发出标题文本。JDBC 读写一律 `Mono.fromCallable(...).subscribeOn(boundedElastic)`；service 只产出标题文本，SSE 事件封装留给 controller。

- [x] Step 1: 实现 generateIfAbsent；prompt："请把下面这段用户首句概括为会话标题：与用户消息同语言、不超过20字、不加引号和任何前后缀，只输出标题本身。"（送入首条消息前 200 字）
- [x] Step 2: 编译 + 启动冒烟（无标题会话发送消息时日志出现标题生成路径）

### Task 4: controller — chatSse 注入 title 事件

**Files:**
- Modify: `agent-app/src/main/java/com/example/agent/controller/AgentScopeController.java:52` 附近（chatSse）

**Interfaces:**
- Consumes: Task 3 的 generateIfAbsent；现有 AgentSseEvent / toSseEvent 转换
- Produces: SSE 事件 `{"type":"title","content":"<标题>"}`（复用 AgentSseEvent 现有字段；动工前核实 DTO 字段命名，如有校验再扩展）。

- [x] Step 1: 先核实 AgentSseEvent 字段与前端解析约定
- [x] Step 2: chatSse 入口取首条用户消息文本 → `sessionTitleService.generateIfAbsent(...)` → `main.mergeWith(titleMono.map(转TITLE事件))`（若核实前端 done 后仍持续读到连接关闭，可简化为主流后 concat）
- [x] Step 3: curl 新会话首条消息：SSE 流出现 title 事件且主回复正常

### Task 5: controller — getSessions 读取优先级

**Files:**
- Modify: `agent-app/src/main/java/com/example/agent/controller/AgentScopeController.java:168` 附近（getSessions）

**Interfaces:**
- Consumes: agentStateStore.get(userId, sessionId, "session_meta", SessionMeta.class)

- [x] Step 1: 每会话优先取 meta.title，缺省回退现有首条消息截断逻辑（存量会话零变化）
- [x] Step 2: curl 会话列表：旧会话标题不变、新会话为 AI 标题

### Task 6: 前端 — title 事件分支

**Files:**
- Modify: `agent-ui/src/stores/chat.ts`（SSE 解析分发处）
- Modify: 会话 VO 字段映射处（如与 getSessions 字段名不一致则同步）

- [x] Step 1: chat.ts 分发加 `type==='title'` 分支：更新当前会话标题并反映到侧栏；不在消息区渲染条目
- [x] Step 2: 刷新页面/切换会话后标题与后端一致（getSessions 回读）

### Task 7: 端到端验证

- [x] 停旧进程、重启 agent-app
- [x] DB：按 application 配置连接执行 `SELECT state_key, state_data FROM agentscope_sessions WHERE state_key='session_meta' LIMIT 5;` 出现含 title 的行
- [x] UI：新会话首条消息 → 侧栏秒级更新为 AI 标题；刷新仍在；旧会话不受影响
- [ ] 同会话第二条消息不触发生成（无 title 事件）—— 用户自行验证（用户明确要求本轮代码直改、不做验证）
- [x] token 统计面板仍只有 qwen3.7-plus，无 flash 记录

### Task 8: 删除会话时同时删除标题记录

**结论（2026-09-30 核查）：现有代码已满足，无需改动。**

- 删除链路：`AgentScopeController.delSessions`（AgentScopeController.java:224）调用官方 `MysqlAgentStateStore.delete(userId, sessionId)`；该实现按 slot=`userId:sessionId` 整槽 `DELETE FROM agentscope_sessions WHERE session_id = ?`（sources jar `MysqlAgentStateStore.java:650-668`），`state_key='session_meta'` 的标题行随之删除。
- 竞态防护：`SessionTitleService.saveTitle`（SessionTitleService.java:92-100）写入前复查 `agent_state` 行，已不存在则跳过写入；`generateIfAbsent`（line 48-52）对不存活的会话跳过标题生成——标题流式生成期间会话被删的场景不会回写孤儿标题。
- 验证：用户自行验证（用户明确要求）。
