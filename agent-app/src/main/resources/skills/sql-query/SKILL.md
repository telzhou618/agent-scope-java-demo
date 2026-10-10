---
name: sql-query
description: 当用户要求查询数据库、统计数据、查看表结构、分析业务数据（如"查一下有哪些表""统计最近的订单量"）时使用。通过 listTables / getTableInfo / executeQuery 三个工具对当前 MySQL 数据源执行只读查询。
---

你拥有三个数据库工具，操作的是当前应用的 MySQL 数据源：

- `listTables`：列出数据库中所有表及注释。
- `getTableInfo(tableName)`：查看某张表的字段、类型、注释。
- `executeQuery(sql)`：执行一条只读 SQL，返回结果集。

## 查询流程

1. 不确定有哪些表时，先调 `listTables`，根据表名和注释定位目标表。
2. 写 SQL 前必须先调 `getTableInfo` 确认字段名和类型，不要凭猜测写字段。
3. 再调 `executeQuery` 执行查询；如果报错，根据错误信息修正 SQL 后重试。

## SQL 编写规范

1. 只允许只读语句（SELECT / WITH / SHOW / DESC / EXPLAIN），任何写操作（INSERT/UPDATE/DELETE/DDL）都会被拒绝，也不要尝试。
2. 一次只执行一条 SQL，不要拼接多语句。
3. 必须控制返回行数：显式写 `LIMIT`（探索性查询建议 20 以内）；不写 LIMIT 时工具会自动追加 `LIMIT 100`。需要总量时用 `COUNT(*)`，不要把全量数据拉回来数。
4. 表名、字段名用反引号包裹（如 `` `user` ``、`` `order_id` ``），避免与关键字冲突。
5. 统计类查询优先在 SQL 里完成聚合（`COUNT`、`SUM`、`AVG`、`GROUP BY`），只把聚合结果拿回来，而不是拉明细到上下文里再算。
6. 时间范围过滤要明确（如 `WHERE created_at >= '2026-01-01'`），字段类型以 `getTableInfo` 返回为准。
7. 查询涉及多张表时用 `JOIN`，子查询语句里的表同样受只读校验约束。
8. 数据必须来自工具真实返回，禁止编造；查询结果为空就如实说明，并给出可能的原因（表名不对、条件太严等）。

## 结果呈现

- 结果集用 Markdown 表格展示，列名翻译成用户能理解的中文。
- 先给结论，再给数据；数据量大时只展示关键行，并说明总行数。
- 用户需要图表时，可配合 data-chart 技能把查询结果可视化。
