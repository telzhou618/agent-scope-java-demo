# Token 月度限额 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **执行方式（用户指定）：** 一次性连续完成全部任务，**任务之间不单独 commit，最后也不 commit**，由用户检查后手动提交。

**Goal:** 给每个 Agent 配置独立的月度 token 限额，超限后拒绝新请求，并在个人中心展示当月总量与每个 Agent 的消耗。

**Architecture:** 复用现有 `t_token_usage` 落库链（`TokenUsageMiddleware` 已按 `user_id + agent_name` 记录每次模型调用的 input/output tokens），不新建表，用 SQL 实时聚合「本月」消耗做校验与展示。限额口径 = input + output tokens（与现有 `UsageSummaryVO.tokens`、费用计算一致），cached/reasoning 不计。配额写在 `application.yaml` 的 `agents.<name>.monthly-token-quota`，`0` = 不限额；按自然月（每月 1 日 00:00 起）统计，按「用户 × Agent」维度逐用户生效；管理员免限额（与项目【管理员放行一切】的既有约定一致）。

**Tech Stack:** Spring Boot 4.1 / Java 17、MyBatis-Plus + MyBatis XML、AgentScope Java、Sa-Token、Vue 3 + TS + Naive UI + UnoCSS。无新增依赖。

**Spec:** 对话内需求（4 条）：① 每个 Agent 单独限额；② 个人中心显示当月总量与每个 Agent 的消耗；③ 限额方式为 token 数；④ 每个 Agent 的月度限额配置在 yaml 的 `agents` 中。关键设计决策：
- **限额维度 = 用户 × Agent**：`agents.<name>.monthly-token-quota` 是每个档位的全局值，作用到每个用户当月对应该档位的消耗（个人中心是「我的」视图，数据天然按 user 归属）。
- **默认给三个 Agent 各配 100 万（1000000）token 限额**；`0` 仍保留为「不限额」语义。
- **管理员免限额**。
- **token 口径 = input + output**（cached/reasoning 不参与）。
- **自然月重置**，`create_time >= 本月 1 日 00:00`。
- **超限在请求开始拦截**：`used >= quota` 即拒绝新请求；单次已在执行中的请求/单个定时任务运行跨过界限时不中断（记录时会自然超一点，属可接受的已知限制）。
- **不新建表**，实时聚合；并发下 `record()` 是异步落库，校验可能晚于实际写入一个极短窗口，单实例演示场景可接受。

## Global Constraints

- 新配置字段名固定为 `monthly-token-quota`（yaml）/ `monthlyTokenQuota`（Java/前端），类型 `Long`，token 单位；**三个 Agent 默认均为 `1000000`**，`0` 或空 = 不限额。
- 每个 Agent（flash/plus/max）都必须有该配置项。
- 用户可见文案用中文；超限提示包含档位展示名与已用/限额数字。
- 不改 `agent-ui` 项目，只改 `naive-agent-ui`。
- 不新增数据库表。
- 本次不 commit（用户手动提交）。

## Review Focus

1. **边界 `used == quota` 也必须拦截**（用 `>=`），`quota <= 0` 必须放行 —— Task 2 的 `overQuotaThrows` / `exactQuotaThrows` / `unlimitedNeverThrows`。
2. **管理员不能被限额** —— Task 2 的 `adminExempt`。
3. **个人中心要列出「本月零消耗」的 Agent**（合并展示），总量 = 各 Agent 之和，空统计时也不能返回 NPE —— Task 2 的 `monthlyUsageIncludesZeroUsageAndTotals`。
4. **只看本月**：传给聚合的 `start` 必须是本月 1 日 00:00（上月数据不参与）—— Task 2 的 `usesMonthStartWindow`。
5. **定时任务执行同样产生 token 且需被拦截**，否则用户可用定时任务绕过聊天限额 —— Task 3 的行内校验（单个 Quartz Job 不易单测，靠 `mvn test` 编译 + 手工验证「额度用尽后定时任务失败并发送系统消息」）。

---

### Task 1: 后端配额配置 + AgentInfo 暴露

**Files:**
- Modify: `agent-app/src/main/resources/application.yaml:41-48`
- Modify: `agent-app/src/main/java/com/example/agent/agent/AbstractAgentFactory.java`
- Modify: `agent-app/src/main/java/com/example/agent/agent/FlashAgentFactory.java`
- Modify: `agent-app/src/main/java/com/example/agent/agent/PlusAgentFactory.java`
- Modify: `agent-app/src/main/java/com/example/agent/agent/MaxAgentFactory.java`
- Modify: `agent-app/src/main/java/com/example/agent/agent/AgentInfo.java`

**Interfaces:**
- Produces: `AgentInfo.monthlyTokenQuota`（`Long`，`0` = 不限）；`AbstractAgentFactory.monthlyTokenQuota()` 抽象方法。

- [ ] **Step 1: 在 yaml 给三个档位加 `monthly-token-quota`（默认各 100 万）**

在 `agents.flash/plus/max` 各加一行（紧跟 `model`）：
```yaml
    monthly-token-quota: ${AGENT_FLASH_MONTHLY_QUOTA:1000000}
    # plus: ${AGENT_PLUS_MONTHLY_QUOTA:1000000}
    # max:   ${AGENT_MAX_MONTHLY_QUOTA:1000000}
```

- [ ] **Step 2: `AgentInfo` 加字段**

在 `AgentInfo.java` 的 `defaultAgent` 字段之后加：
```java
@Schema(description = "月度 token 限额（0=不限）")
private Long monthlyTokenQuota;
```

- [ ] **Step 3: `AbstractAgentFactory` 加抽象模板方法并填入 `agentInfo()`**

新增抽象方法（放在 `scheduleEnabled()` 附近）：
```java
protected abstract Long monthlyTokenQuota();
```
在 `agentInfo()` 的 builder 里加 `.monthlyTokenQuota(monthlyTokenQuota())`。

- [ ] **Step 4: 三个子类注入并覆写**

每个子类在 `@Value("${agents.<name>.model}")` 旁加：
```java
@Value("${agents.<name>.monthly-token-quota:1000000}")
private Long monthlyTokenQuota;

@Override
protected Long monthlyTokenQuota() { return monthlyTokenQuota; }
```

- [ ] **Step 5: 编译验证**

Run: `mvn -pl agent-app test`
Expected: 编译通过，既有多例测试全绿（14 run / 0 fail / 1 skipped）。

---

### Task 2: 配额统计与校验核心

**Files:**
- Create: `agent-app/src/main/java/com/example/agent/vo/AgentUsageRow.java`
- Create: `agent-app/src/main/java/com/example/agent/vo/MonthlyQuotaVO.java`
- Create: `agent-app/src/main/java/com/example/agent/service/TokenQuotaService.java`
- Modify: `agent-app/src/main/java/com/example/agent/mapper/TokenUsageMapper.java`
- Modify: `agent-app/src/main/resources/mapper/TokenUsageMapper.xml`
- Test: `agent-app/src/test/java/com/example/agent/service/TokenQuotaServiceTest.java`

**Interfaces:**
- Consumes: Task 1 的 `AgentInfo.monthlyTokenQuota`；`AgentRegistry.getAgentInfo(String)`；`AgentPermissionService.filterAgents(long)`；`UserMapper.selectById(Long)`。
- Produces（后续任务依赖）：
  - `TokenQuotaService.checkQuota(long userId, String agentName)`（超限抛 `BizException(ErrorCode.FORBIDDEN, ...)`，否则无副作用）
  - `TokenQuotaService.monthlyUsage(long userId)` → `MonthlyQuotaVO`
  - `TokenUsageMapper.sumCurrentMonthByAgent(Long userId, LocalDateTime start)` → `List<AgentUsageRow>`
  - `AgentUsageRow { String agentName; Long tokens; }`
  - `MonthlyQuotaVO { String month; long totalTokens; List<AgentItem> agents; }`，`AgentItem { String name; String displayName; Long quotaTokens; long usedTokens; }`

- [ ] **Step 1: 写失败测试 `TokenQuotaServiceTest`**

新建测试类（JUnit 5 + Mockito，参考 `UserPermissionServiceTest`），mock `UserMapper` / `TokenUsageMapper` / `AgentPermissionService`，用 `@InjectMocks` 注入 `TokenQuotaService`。用例与断言：

```java
// admin 免限额：checkQuota 不抛异常（stub admin user 且设 quota>0）
// unlimited：quota 为 null 或 0 时 checkQuota 不抛（stub 非 admin user）
// underQuota：used < quota 时不抛
// exactQuotaThrows：used == quota 时抛 BizException
// overQuotaThrows：used > quota 时抛 BizException（断言 code==403、message 含 displayName）
// monthlyUsageIncludesZeroUsageAndTotals：
//   filterAgents 返回 [flash(quota=100), max(quota=0)]，聚合仅返回 flash used=30
//   → monthlyUsage 返回 2 个 agent，max.usedTokens==0，totalTokens==30
// usesMonthStartWindow：
//   verify tokenUsageMapper.sumCurrentMonthByAgent(eq(userId), argThat(start ->
//     start.equals(LocalDate.now().withDayOfMonth(1).atStartOfDay())))
```

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -pl agent-app -Dtest=TokenQuotaServiceTest test`
Expected: FAIL（`TokenQuotaService` / `AgentUsageRow` / `MonthlyQuotaVO` 不存在）。

- [ ] **Step 3: 建 VO `AgentUsageRow`、`MonthlyQuotaVO`**

两个 `@Data` 类，字段见 Interface 块；`MonthlyQuotaVO.AgentItem` 用静态内部类（参考 `UsageSummaryVO.Totals`）。

- [ ] **Step 4: mapper 加聚合方法**

`TokenUsageMapper.java` 加：
```java
List<AgentUsageRow> sumCurrentMonthByAgent(
        @Param("userId") Long userId,
        @Param("start") LocalDateTime start);
```
`TokenUsageMapper.xml` 加（namespace 已有）：
```xml
<select id="sumCurrentMonthByAgent" resultType="com.example.agent.vo.AgentUsageRow">
    SELECT agent_name AS agentName, SUM(input_tokens + output_tokens) AS tokens
    FROM t_token_usage
    WHERE user_id = #{userId} AND create_time &gt;= #{start}
    GROUP BY agent_name
</select>
```

- [ ] **Step 5: 实现 `TokenQuotaService`**

`@Service @RequiredArgsConstructor`，注入 `UserMapper`、`TokenUsageMapper`、`AgentRegistry`、`AgentPermissionService`。

- `checkQuota(long userId, String agentName)`：`selectById` 见 admin 直接 return；`agentRegistry.getAgentInfo(agentName)` 取 `monthlyTokenQuota`，`null`/`<=0` return；`used = sumCurrentMonthByAgent(...). 中 agentName 匹配项的 tokens`；`used >= quota` 抛 `BizException(ErrorCode.FORBIDDEN, "本月「" + displayName + "」token 额度已用尽（已用 " + used + " / " + quota + "）")`。
- `monthlyUsage(long userId)`：`start = LocalDate.now().withDayOfMonth(1).atStartOfDay()`；聚合结果 → `Map<String,Long>`；`agentPermissionService.filterAgents(userId)` 每个 agent 映射为 `AgentItem`（`usedTokens = map.getOrDefault(name, 0)`）；`month = YearMonth.now().toString()`；`totalTokens = sum(usedTokens)`。

- [ ] **Step 6: 跑测试确认通过**

Run: `mvn -pl agent-app -Dtest=TokenQuotaServiceTest test`
Expected: PASS。

- [ ] **Step 7: 全量测试**

Run: `mvn -pl agent-app test`（Expected: 0 fail，1 skipped）。

---

### Task 3: 对话与定时任务接入校验

**Files:**
- Modify: `agent-app/src/main/java/com/example/agent/service/AgentChatService.java:48-55`
- Modify: `agent-app/src/main/java/com/example/agent/service/ScheduledTaskJob.java`

**Interfaces:**
- Consumes: `TokenQuotaService.checkQuota(long userId, String agentName)`（Task 2）。

- [ ] **Step 1: 对话入口接入**

`AgentChatService` 注入 `TokenQuotaService`；在 `chatSse` 中 `agentPermissionService.checkAgent(...)` 之后加一行：
```java
tokenQuotaService.checkQuota(loginId, request.getAgentName());
```

- [ ] **Step 2: 定时任务执行接入**

`ScheduledTaskJob` 注入 `TokenQuotaService`；在 `runAgent` 方法开头（`agentRegistry.getAgent(...)` 之前）加：
```java
tokenQuotaService.checkQuota(task.getUserId(), task.getAgentName());
```
（抛出的 `BizException` 会被 `executeInternal` 既有 `catch` 兜住，作为「定时任务执行失败」系统消息告知用户。）

- [ ] **Step 3: 编译 + 全量测试**

Run: `mvn -pl agent-app test`
Expected: 编译通过、0 fail（新增行是纯接线，逻辑已由 Task 2 单测覆盖）。

---

### Task 4: 月度配额查询接口

**Files:**
- Modify: `agent-app/src/main/java/com/example/agent/controller/UsageController.java`

**Interfaces:**
- Consumes: `TokenQuotaService.monthlyUsage(long userId)`（Task 2）。
- Produces: `GET /agent/scope/usage/month-quota` → `Result<MonthlyQuotaVO>`（用户身份自 token 解析）。

- [ ] **Step 1: 加端点**

`UsageController` 注入 `TokenQuotaService`，新增：
```java
@GetMapping("/month-quota")
@Operation(summary = "本月 token 限额与每个 Agent 消耗")
public Result<MonthlyQuotaVO> monthQuota() {
    return Result.okData(quotaService.monthlyUsage(StpUtil.getLoginIdAsLong()));
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl agent-app -DskipTests package`
Expected: BUILD SUCCESS。

---

### Task 5: 个人中心展示本月限额

**Files:**
- Modify: `naive-agent-ui/src/api/types.ts`
- Modify: `naive-agent-ui/src/api/agent.ts`
- Modify: `naive-agent-ui/src/views/ProfileView.vue`

**Interfaces:**
- Consumes: `GET /agent/scope/usage/month-quota`（Task 4）。
- Produces（前端类型）：`MonthlyQuotaSummary { month; totalTokens; agents: AgentQuotaItem[] }`、`AgentQuotaItem { name; displayName; quotaTokens; usedTokens }`。

- [ ] **Step 1: 加前端类型**

`types.ts` 增补：
```ts
export interface AgentQuotaItem {
  name: string
  displayName: string
  /** 0 = 不限 */
  quotaTokens: number
  usedTokens: number
}
export interface MonthlyQuotaSummary {
  month: string
  totalTokens: number
  agents: AgentQuotaItem[]
}
```

- [ ] **Step 2: 加 API 函数**

`agent.ts` 顶部 `import type` 增补 `MonthlyQuotaSummary`，并加：
```ts
export const getMonthlyQuota = () =>
  http.get<Result<MonthlyQuotaSummary>>(`${API_BASE}/usage/month-quota`).then((r) => r.data.data)
```

- [ ] **Step 3: 个人中心加卡片**

`ProfileView.vue`：新增 `quota` ref + `loadQuota()`（`onMounted` 调一次），导入 `getMonthlyQuota`、类型与 `NProgress`。在身份卡 `</NCard>` 之后、`<!-- 用量统计 -->` 之前插入一个新 `NCard`，卡片内：
- 一个 `.stat-card` 显示「本月总消耗：`formatTokens(quota.totalTokens)`」。
- 每个 agent 一行：`displayName` + 已用/限额文案（`formatTokens(used) / formatTokens(quota)` 或「不限」）+ `NProgress`（`percentage = quota>0 ? clamp(used/quota*100, 0, 100) : 0`，`quota<=0` 时显示「不限」不画进度）。
复用 `.stat-card` / `.stat-label` / `.stat-value` 样式与 `formatTokens`。

- [ ] **Step 4: 构建验证**

Run: `pnpm build`（cwd `naive-agent-ui`）
Expected: `vue-tsc --noEmit` 与 `vite build` 均通过。

---

## Self-Review

- **Spec 覆盖**：① 每个 Agent 单独限额 → Task 1（yaml 每档位配置，默认各 100 万）；② 个人中心当月总量 + 每 Agent 消耗 → Task 2/4/5（`monthlyUsage` + 接口 + 卡片）；③ token 数口径 → Task 2（input+output 聚合）；④ yaml 配置 → Task 1。管理员免限额为新增决策，已在 Spec 段标注。
- **Step 扫描**：每个 step 只做一件事且可判定；`TokenQuotaService` 只给签名 + 关键算法（月份窗口、`>=` 边界、合并），未抄写完整方法体。
- **类型一致**：`AgentInfo.monthlyTokenQuota`(Long) ↔ `AgentQuotaItem.quotaTokens`(number) ↔ yaml `monthly-token-quota`；`MonthlyQuotaVO` ↔ `MonthlyQuotaSummary` 字段对齐；`sumCurrentMonthByAgent` 三处（接口/XML/调用）签名一致。默认值统一为 `1000000`。
- **Review Focus**：5 条分别由 Task 2 的 `exactQuotaThrows`/`unlimitedNeverThrows`、`adminExempt`、`monthlyUsageIncludesZeroUsageAndTotals`、`usesMonthStartWindow`，以及 Task 3 的调度侧接线覆盖。
- **比例**：计划长度约等于需求规模，代码块仅保留签名/断言/SQL，未写成实现转录。