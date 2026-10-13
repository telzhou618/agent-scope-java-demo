# 定时任务权限控制 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给用户增加「定时任务」权限、给 Agent 增加「是否支持定时任务」开关，并在菜单/路由/REST/工具执行四层做权限放行，只有拥有该权限的用户 + 开启该能力的 Agent 才能使用定时任务。

**Architecture:** 后端沿用 Sa-Token 权限码机制（`StpInterfaceImpl#getPermissionList` + `@SaCheckPermission`），新增 `UserPermission` 枚举与 `UserPermissionService` 做统一判定（管理员自动放行）；Agent 侧在 `AbstractAgentFactory` 增加 `scheduleEnabled()` 开关，仅开启的档位注册 `ScheduleTools`，工具运行时再校验当前用户权限。前端用 `UserInfo.permissions` 控制菜单/路由，用 `AgentInfo.schedule` 在 Agent 选择器打标识。

**Tech Stack:** Spring Boot 4.1.0 / Java 17 / MyBatis-Plus 3.5.17 / Sa-Token 1.46.0 / Vue 3 + TypeScript + Naive UI + Pinia（naive-agent-ui）。

**Spec:** 本次需求来自对话，无独立 spec 文件。六条要求：
1. 用户表加权限合集字段（当前仅「定时任务」），权限合集定义为枚举。
2. 只有有定时任务权限的用户才能看到菜单、操作定时任务列表。
3. Agent 增加是否支持「定时任务」的配置，开启才添加定时任务 tools。
4. 工具执行时需判断「用户有权限 且 Agent 开启」才放行。
5. 目前只给 max agent 配置开启。
6. ui 端 Agent 选择加定时任务标识；只改 naive-agent-ui，agent-ui 不动。

## Global Constraints

- Java 17；前端 TypeScript 严格模式（`vue-tsc --noEmit` 参与 build）。
- 权限字段存储：`t_user.permissions` 逗号分隔权限码，空串/`NULL` = 无权限，admin 忽略并自动拥有全部权限（与现有 `agents`/`isAdmin` 语义一致）。
- 权限码唯一取值：`schedule`（`UserPermission.SCHEDULE`），标签「定时任务」。注解里的字符串必须与 `UserPermission.SCHEDULE.getCode()` 一致。
- 只修改 `naive-agent-ui`，`agent-ui` 一律不动。
- 不新增任何依赖；测试仅使用现有 JUnit 5 + Mockito（来自 `spring-boot-starter-webmvc-test`）。
- 后端验证命令：`mvn -pl agent-app test`（编译 + 运行单测；依赖私服 Nexus，本地 `.m2` 已缓存即可离线跑）。
- 前端验证命令：`pnpm build`（含 `vue-tsc --noEmit` 类型检查）。naive-agent-ui 无测试脚本，不新增测试框架。
- 每个任务结束一个 commit。

## Review Focus

以下输入/边界是 spec 未明说但最可能漏掉的，逐条落到拥有对应代码的任务的测试/步骤里：

1. **管理员 `permissions` 为空串** —— 仍必须能看到菜单、调用 REST、工具放行。由 `UserPermissionServiceTest.adminAlwaysHasPermission`（Task 2）与前端 `canSchedule` 对 `isAdmin` 的短路（Task 5）钉死。
2. **`permissions` 为 `NULL` / 空白 / 带空格（`" schedule, "`）** —— 均按「无权限」或正确解析，不得 NPE 或误命中。由 `UserPermissionTest`（Task 1）钉死。
3. **普通用户无权限访问 REST** —— `@SaCheckPermission` 抛 `NotPermissionException`，由既有 `GlobalExceptionHandler` 映射 403。Task 2 步骤说明验证方式（无权限用户 curl 应得 403）。
4. **plus 档曾注册过 `ScheduleTools`，历史定时任务仍存 `t_scheduled_task`** —— 本次移除 plus 的工具不影响既存任务列表展示（`listForUser` 与工具无关），无需数据迁移。Task 3 步骤中显式记录该结论。
5. **有权限用户选了不支持定时任务的 Agent（flash/plus）** —— 该档位不注册 schedule 工具，无法通过对话创建任务；前端标识只点亮 max。由 Task 3 的注册逻辑 + Task 4 的 `schedule` 标识共同保证（无单测，靠构建 + 手工验收）。

---

### Task 1: 权限枚举 + 用户表权限字段（数据流）

**Files:**
- Create: `agent-app/src/main/java/com/example/agent/enums/UserPermission.java`
- Modify: `agent-app/src/main/java/com/example/agent/entity/User.java:50-52`
- Modify: `agent-app/src/main/java/com/example/agent/vo/UserVO.java:30-58`
- Modify: `agent-app/src/main/java/com/example/agent/vo/UserManageVO.java:35-66`
- Modify: `agent-app/src/main/java/com/example/agent/dto/UserCreateRequest.java:40-41`
- Modify: `agent-app/src/main/java/com/example/agent/dto/UserUpdateRequest.java:35-39`
- Modify: `agent-app/src/main/java/com/example/agent/service/UserManageService.java:48-128`
- Modify: `sql/init.sql:19-37`（建表列）、`sql/init.sql:160-172`（升级 ALTER 注释）
- Test: `agent-app/src/test/java/com/example/agent/enums/UserPermissionTest.java`

**Interfaces:**
- Produces:
  - `enum UserPermission { SCHEDULE }`，方法：`getCode()`、`getLabel()`、`static List<String> allCodes()`、`static List<String> parse(String csv)`、`static boolean contains(String csv, UserPermission p)`。
  - `User.permissions`（`String`，逗号分隔权限码）。
  - `UserVO.permissions` / `UserManageVO.permissions`（`List<String>`）。

- [ ] **Step 1: 写失败单测 `UserPermissionTest`**

```java
class UserPermissionTest {
    @Test void parseHandlesNullOrBlank() {
        assertEquals(List.of(), UserPermission.parse(null));
        assertEquals(List.of(), UserPermission.parse(""));
        assertEquals(List.of(), UserPermission.parse("  "));
    }
    @Test void parseTrimsAndDropsEmpty() {
        assertEquals(List.of("schedule", "other"), UserPermission.parse(" schedule, , other "));
    }
    @Test void containsMatchesCode() {
        assertTrue(UserPermission.contains("schedule,other", UserPermission.SCHEDULE));
        assertFalse(UserPermission.contains("other", UserPermission.SCHEDULE));
        assertFalse(UserPermission.contains(null, UserPermission.SCHEDULE));
    }
    @Test void allCodesHasEveryValue() {
        assertEquals(Arrays.stream(UserPermission.values()).map(UserPermission::getCode).toList(),
                UserPermission.allCodes());
    }
}
```

- [ ] **Step 2: 跑测试确认失败** — Run: `mvn -pl agent-app -Dtest=UserPermissionTest test`，Expected: 编译失败（`UserPermission` 不存在）。

- [ ] **Step 3: 创建枚举 `UserPermission`（code=`schedule`，label=`定时任务`）**

实现 `getCode/getLabel/allCodes/parse/contains`；`parse` 对 `null`/blank 返回 `List.of()`，按 `,` 拆分、`trim`、过滤空串；`contains` = `parse(csv).contains(p.getCode())`。

- [ ] **Step 4: 跑测试确认通过** — Run: `mvn -pl agent-app -Dtest=UserPermissionTest test`，Expected: PASS。

- [ ] **Step 5: `User` 加字段**

在 `agents` 之后加 `private String permissions;` 注释 `// 逗号分隔权限码，空=无权限（管理员不受限）`。

- [ ] **Step 6: `UserVO.from` / `UserManageVO.from` 输出 `permissions`**

两处都加 `private List<String> permissions;`，`from()` 里 `vo.setPermissions(UserPermission.parse(user.getPermissions()));`。

- [ ] **Step 7: `UserCreateRequest` / `UserUpdateRequest` 加 `permissions`**

`private List<String> permissions;`（`@Schema` 注释：空=无权限；管理员忽略/强制为空）。

- [ ] **Step 8: `UserManageService.create()` 持久化 permissions**

在 `user.setAgents(...)` 之后加：`user.setPermissions(isAdmin ? "" : joinPermissions(request.getPermissions()));`，并新增私有方法 `joinPermissions(List<String>)`（镜像已有 `joinAgents`：null/空返回 `""`，否则 `String.join(",", trim 后非空项)`）。

- [ ] **Step 9: `UserManageService.update()` 更新 permissions**

在 `agents` 块之后复用同一 `willBeAdmin`：
```java
if (willBeAdmin) {
    if (request.getIsAdmin() != null || request.getPermissions() != null) {
        update.setPermissions("");
    }
} else if (request.getPermissions() != null) {
    update.setPermissions(joinPermissions(request.getPermissions()));
}
```

- [ ] **Step 10: 更新 `sql/init.sql`**

建表 `t_user` 在 `agents` 行后加：
```sql
permissions VARCHAR(100) NOT NULL DEFAULT '' COMMENT '逗号分隔权限码，空=无权限' AFTER agents,
```
并在「已有库升级」ALTER 注释块（第 160-172 行）追加：
```sql
--   ALTER TABLE t_user ADD COLUMN permissions VARCHAR(100) NOT NULL DEFAULT '' COMMENT '逗号分隔权限码，空=无权限' AFTER agents;
```

- [ ] **Step 11: 全量编译 + 单测** — Run: `mvn -pl agent-app test`，Expected: BUILD SUCCESS。

- [ ] **Step 12: Commit**

```bash
git add agent-app/src/main/java/com/example/agent/enums/UserPermission.java \
        agent-app/src/main/java/com/example/agent/entity/User.java \
        agent-app/src/main/java/com/example/agent/vo/UserVO.java \
        agent-app/src/main/java/com/example/agent/vo/UserManageVO.java \
        agent-app/src/main/java/com/example/agent/dto/UserCreateRequest.java \
        agent-app/src/main/java/com/example/agent/dto/UserUpdateRequest.java \
        agent-app/src/main/java/com/example/agent/service/UserManageService.java \
        agent-app/src/test/java/com/example/agent/enums/UserPermissionTest.java \
        sql/init.sql
git commit -m "feat(user): add schedule permission enum and t_user.permissions field"
```

---

### Task 2: 权限服务 + REST 鉴权

**Files:**
- Create: `agent-app/src/main/java/com/example/agent/service/UserPermissionService.java`
- Modify: `agent-app/src/main/java/com/example/agent/service/StpInterfaceImpl.java:20-26`
- Modify: `agent-app/src/main/java/com/example/agent/controller/ScheduleController.java:25-29`
- Test: `agent-app/src/test/java/com/example/agent/service/UserPermissionServiceTest.java`

**Interfaces:**
- Consumes: `UserPermission`（Task 1）、`User.permissions`。
- Produces:
  - `UserPermissionService.hasPermission(long userId, UserPermission permission) -> boolean`
  - `UserPermissionService.requirePermission(long userId, UserPermission permission) -> void`（抛 `BizException(ErrorCode.FORBIDDEN, "无「"+permission.getLabel()+"」权限")`）。

- [ ] **Step 1: 写失败单测 `UserPermissionServiceTest`**（`@ExtendWith(MockitoExtension.class)`，`@Mock UserMapper`，`@InjectMocks UserPermissionService`）

四个用例：
```java
@Test void adminAlwaysHasPermission() // isAdmin=1, permissions="" -> hasPermission true
@Test void normalUserWithCodeHasPermission() // isAdmin=0, permissions="schedule" -> true
@Test void normalUserWithoutCodeDenied()    // isAdmin=0, permissions="" -> false
@Test void missingUserDenied()              // selectById -> null -> false
```

- [ ] **Step 2: 跑测试确认失败** — Run: `mvn -pl agent-app -Dtest=UserPermissionServiceTest test`，Expected: 编译失败（`UserPermissionService` 不存在）。

- [ ] **Step 3: 实现 `UserPermissionService`（`@Service @RequiredArgsConstructor`，注入 `UserMapper`）**

`hasPermission`：`user==null -> false`；`isAdmin==1 -> true`；否则 `UserPermission.contains(user.getPermissions(), permission)`。`requirePermission`：`!hasPermission(...)` 时抛 `BizException(ErrorCode.FORBIDDEN, "无「"+permission.getLabel()+"」权限")`。

- [ ] **Step 4: 跑测试确认通过** — Run: `mvn -pl agent-app -Dtest=UserPermissionServiceTest test`，Expected: PASS。

- [ ] **Step 5: `StpInterfaceImpl.getPermissionList` 返回权限码**

```java
@Override
public List<String> getPermissionList(Object loginId, String loginType) {
    User user = userMapper.selectById(Long.valueOf(String.valueOf(loginId)));
    if (user != null && user.getIsAdmin() != null && user.getIsAdmin() == 1) {
        return UserPermission.allCodes();
    }
    return user == null ? List.of() : UserPermission.parse(user.getPermissions());
}
```

- [ ] **Step 6: `ScheduleController` 类级加 `@SaCheckPermission("schedule")`**

import `cn.dev33.satoken.annotation.SaCheckPermission`；注解加在 `@RequestMapping` 之后（与 `UserManageController` 的 `@SaCheckRole("admin")` 位置一致）。注解字符串必须是 `UserPermission.SCHEDULE.getCode()` 的值 `"schedule"`。

- [ ] **Step 7: 编译验证** — Run: `mvn -pl agent-app test`，Expected: BUILD SUCCESS。手工抽验：无权限普通用户 `GET /agent/scope/schedule/list` 返回 403（`GlobalExceptionHandler` 已映射 `NotPermissionException`→403）。

- [ ] **Step 8: Commit**

```bash
git add agent-app/src/main/java/com/example/agent/service/UserPermissionService.java \
        agent-app/src/main/java/com/example/agent/service/StpInterfaceImpl.java \
        agent-app/src/main/java/com/example/agent/controller/ScheduleController.java \
        agent-app/src/test/java/com/example/agent/service/UserPermissionServiceTest.java
git commit -m "feat(schedule): gate schedule REST endpoints by user permission"
```

---

### Task 3: Agent 定时任务开关 + 工具执行校验

**Files:**
- Modify: `agent-app/src/main/java/com/example/agent/agent/AgentInfo.java:42-46`
- Modify: `agent-app/src/main/java/com/example/agent/agent/AbstractAgentFactory.java:63-109,132-197,238-256`
- Modify: `agent-app/src/main/java/com/example/agent/agent/MaxAgentFactory.java:3-8,31-38,80-87`
- Modify: `agent-app/src/main/java/com/example/agent/agent/PlusAgentFactory.java:3-6,29-30,72-77`
- Modify: `agent-app/src/main/java/com/example/agent/agent/FlashAgentFactory.java:5`
- Modify: `agent-app/src/main/java/com/example/agent/tools/ScheduleTools.java:19-27,121-130`

**Interfaces:**
- Consumes: `UserPermissionService.hasPermission`（Task 2）、`UserPermission.SCHEDULE`（Task 1）。
- Produces: `AgentInfo.schedule`（`boolean`）、`AbstractAgentFactory.scheduleEnabled()`（`protected boolean`，默认 `false`）、`ScheduleTools` 构造器加第三参 `UserPermissionService`。

**关键结论（写进本任务自查）：** requirement 4 的「Agent 开启定时任务」维度由工具注册期保证——只有 `scheduleEnabled()==true` 的档位才注册 `ScheduleTools`，非启用档位根本不存在该工具无法执行；「用户有权限」维度在工具方法内运行时校验。plus 移除工具不影响其历史 `t_scheduled_task` 列表（`listForUser` 不依赖工具）。

- [ ] **Step 1: `AgentInfo` 加字段** — `@Schema(description = "是否支持定时任务") private boolean schedule;`（放在 `attachments` 之后）。

- [ ] **Step 2: `AbstractAgentFactory` 加开关与注册**

  - 加 `@Autowired protected ScheduleService scheduleService;`、`@Autowired protected UserPermissionService userPermissionService;` 与相应 import。
  - 加 `protected boolean scheduleEnabled() { return false; }`（放 `defaultAgent()` 附近）。
  - `agentInfo()` 里加 `.schedule(scheduleEnabled())`。
  - `buildToolkit()` 中，在 `registerLocalTools(toolkit);` 之后加：
    ```java
    if (scheduleEnabled()) {
        toolkit.registerTool(new ScheduleTools(scheduleService, name(), userPermissionService));
    }
    ```

- [ ] **Step 3: `MaxAgentFactory` 打开开关、移除手工注册**

删除 `ScheduleService` 的 `@Autowired` 与 `import`、删除 `toolkit.registerTool(new ScheduleTools(scheduleService, name()));` 与 `ScheduleTools` import；新增 `@Override protected boolean scheduleEnabled() { return true; }`。

- [ ] **Step 4: `PlusAgentFactory` 移除定时任务工具**

删除 `ScheduleService` 的 `@Autowired`/`import`、`new ScheduleTools(...)` 注册行、`ScheduleTools` import。不新增 `scheduleEnabled()`（沿用默认 `false`）。

- [ ] **Step 5: `FlashAgentFactory` 清理未用 import** — 删除第 5 行 `import com.example.agent.tools.ScheduleTools;`（该文件本就不注册）。

- [ ] **Step 6: `ScheduleTools` 运行时校验用户权限**

  - 构造器改为 `ScheduleTools(ScheduleService scheduleService, String agentName, UserPermissionService userPermissionService)`，存 `private final UserPermissionService userPermissionService;`。
  - import `UserPermission`、`UserPermissionService`。
  - 5 个工具方法（`createSchedule/listSchedules/updateSchedule/deleteSchedule/triggerSchedule`）在 `if (userId == null) return fail("无法识别当前用户");` 之后加：
    ```java
    if (!userPermissionService.hasPermission(userId, UserPermission.SCHEDULE)) {
        return fail("无「定时任务」权限");
    }
    ```

- [ ] **Step 7: 编译验证** — Run: `mvn -pl agent-app test`，Expected: BUILD SUCCESS。构建产物中仅 max 的 `AgentInfo.schedule == true` 可通过 `/agents` 接口观察。

- [ ] **Step 8: Commit**

```bash
git add agent-app/src/main/java/com/example/agent/agent/AgentInfo.java \
        agent-app/src/main/java/com/example/agent/agent/AbstractAgentFactory.java \
        agent-app/src/main/java/com/example/agent/agent/MaxAgentFactory.java \
        agent-app/src/main/java/com/example/agent/agent/PlusAgentFactory.java \
        agent-app/src/main/java/com/example/agent/agent/FlashAgentFactory.java \
        agent-app/src/main/java/com/example/agent/tools/ScheduleTools.java
git commit -m "feat(agent): gate schedule tools by user permission and agent schedule flag"
```

---

### Task 4: 前端类型 + Agent 定时任务标识

**Files:**
- Modify: `naive-agent-ui/src/api/types.ts:18-57`
- Modify: `naive-agent-ui/src/components/AgentSelect.vue:4-13,25-31`

**Interfaces:**
- Consumes: 后端 `UserVO.permissions`、`AgentInfo.schedule`（Task 1/3）。
- Produces: `AgentInfo.schedule: boolean`、`UserInfo.permissions: string[]`。

- [ ] **Step 1: `types.ts` 加字段** — `UserInfo` 加 `/** 权限码列表；管理员自动拥有全部 */ permissions: string[]`；`AgentInfo` 加 `/** 是否支持定时任务 */ schedule: boolean`。

- [ ] **Step 2: `AgentSelect.vue` 加定时任务标识**

  - import `TimeOutline` from `@vicons/ionicons5`。
  - `CAPABILITIES` 的 key 联合类型加 `'schedule'`，数组尾加 `{ key: 'schedule', label: '定时任务', icon: TimeOutline }`。其余渲染逻辑不动（支持实色、不支持灰显）。

- [ ] **Step 3: 类型检查** — Run: `pnpm build`（在 `naive-agent-ui`），Expected: `vue-tsc --noEmit` 通过、构建成功。

- [ ] **Step 4: Commit**

```bash
git add naive-agent-ui/src/api/types.ts naive-agent-ui/src/components/AgentSelect.vue
git commit -m "feat(ui): add schedule badge to agent selector"
```

---

### Task 5: 前端菜单 + 路由守卫

**Files:**
- Modify: `naive-agent-ui/src/layouts/AppLayout.vue:138-153`
- Modify: `naive-agent-ui/src/router/index.ts:46-66`

**Interfaces:**
- Consumes: `UserInfo.permissions`（Task 4 的 `types.ts`）。

- [ ] **Step 1: `AppLayout.vue` 加 `canSchedule` 并条件化菜单**

  - 脚本加 `const canSchedule = computed(() => !!auth.user && (auth.user.isAdmin || auth.user.permissions?.includes('schedule')))`。
  - `accountOptions` 初始数组只留「个人中心」；随后 `if (canSchedule.value) options.push({ label: '定时任务', key: 'schedules', icon: () => h(NIcon, null, { default: () => h(TimeOutline) }) })`；`admin` 判断保持不动，置于其后。

- [ ] **Step 2: `router/index.ts` 加 schedules 守卫**

  在 `admin` 守卫之后、`return true` 之前加：
  ```ts
  if (to.name === 'schedules') {
    const { useAuthStore } = await import('../stores/auth')
    const auth = useAuthStore()
    if (!auth.user) await auth.fetchCurrent()
    const allowed = auth.user?.isAdmin === true || auth.user?.permissions?.includes('schedule')
    if (!allowed) return { path: '/chat' }
  }
  ```

- [ ] **Step 3: 类型检查** — Run: `pnpm build`，Expected: 通过。

- [ ] **Step 4: Commit**

```bash
git add naive-agent-ui/src/layouts/AppLayout.vue naive-agent-ui/src/router/index.ts
git commit -m "feat(ui): gate schedule menu and route by user permission"
```

---

### Task 6: 管理端用户权限勾选

**Files:**
- Modify: `naive-agent-ui/src/api/user.ts:9-37`
- Modify: `naive-agent-ui/src/components/admin/UsersPanel.vue:81-107,118-128,136-148,153-187,427-439`

**Interfaces:**
- Consumes: 后端 `UserManageVO.permissions`（Task 1）、`UserCreateRequest/UserUpdateRequest.permissions`。

- [ ] **Step 1: `api/user.ts` 类型对接** — `UserManageItem` 加 `permissions: string[]`；`UserSavePayload` 加 `permissions?: string[]`。

- [ ] **Step 2: `UsersPanel.vue` 表单接入 permissions**

  - `UserForm` 加 `permissions: string[]`；`form` 初始 `permissions: []`；`resetForm` 加 `form.permissions = []`。
  - `openEdit` 加 `form.permissions = [...(item.permissions ?? [])]`。
  - `submitForm` 的 `payload` 加 `permissions: [...form.permissions]`。

- [ ] **Step 3: `UsersPanel.vue` 模板加权限勾选**

  在「Agents」`NFormItem` 之后加：
  ```html
  <NFormItem label="权限">
    <div class="flex flex-col gap-1">
      <NCheckboxGroup v-model:value="form.permissions" :disabled="form.isAdmin">
        <NCheckbox value="schedule">定时任务</NCheckbox>
      </NCheckboxGroup>
      <span v-if="form.isAdmin" class="text-xs op-50">管理员拥有全部权限</span>
    </div>
  </NFormItem>
  ```
  （`NCheckboxGroup`/`NCheckbox` 已在本文件 import。）

- [ ] **Step 4: 类型检查** — Run: `pnpm build`，Expected: 通过。

- [ ] **Step 5: Commit**

```bash
git add naive-agent-ui/src/api/user.ts naive-agent-ui/src/components/admin/UsersPanel.vue
git commit -m "feat(ui): add schedule permission checkbox in user management"
```

---

## 端到端验收（全部任务完成后手工执行）

1. 启动 mcp-server（`:8081`）与 agent-app（`:8082`），`naive-agent-ui` `pnpm dev`（`:5174`）。
2. 管理员在用户管理里给某个普通用户勾选「定时任务」，该用户重新登录后账号菜单出现「定时任务」，可进入并操作列表；未勾选用户无此菜单且直连 `/schedules` 被重定向回 `/chat`。
3. 未授权用户 `curl GET /agent/scope/schedule/list` 返回 403。
4. Agent 选择器里仅 **Agent Max** 的「定时任务」标识点亮（flash/plus 灰显）。
5. 用有权限用户选 max 对话「每周一 9 点把上周运营报告发我」，任务可创建；选 plus 输入同指令，无 schedule 工具、不能创建。
6. 历史 plus 定时任务在列表中仍可见可操作（无迁移影响）。