# 数据源使用规范：agent-demo（应用主库）

## 数据源说明

- 该库为应用自身的主库 `agent_demo`，仅作多数据源示例，供只读查询演示使用。
- 所有查询均为只读（SELECT/WITH/SHOW/DESC/EXPLAIN），禁止任何写操作。

## 主要表

| 表名 | 说明 |
| --- | --- |
| `t_user` | 用户表，含登录账号、昵称、权限、头像等 |
| `t_token_usage` | token 消耗记录 |
| `agentscope_sessions` | AgentScope 会话状态（state_data 为 JSON，不建议直接查询明细） |
| `t_message` | 会话消息 |
| `t_scheduled_task` | 定时任务 |
| `t_message_feedback` | 消息反馈（up/down） |
| `t_user_feedback` | 意见反馈 |
| `t_operation_log` | 操作日志 |
| `t_user_oauth` | 第三方登录绑定 |
| `t_file` | 用户文件元数据 |

## 约定与注意事项

1. 表名/字段名在 SQL 里用反引号包裹，避免与关键字冲突。
2. 时间字段多为 `DATETIME`，比较时写完整范围；统计用 `COUNT/SUM/AVG` 下推，不要拉明细回来算。
3. 涉及用户敏感信息时只取必要字段，不要全表 `SELECT *`。
4. `t_token_usage`、`t_operation_log` 体量可能较大，查询务必带时间范围与 `LIMIT`。
5. `t_user.password` 为 BCrypt 密文，属敏感字段，禁止在结果中返回密码。