---
name: sql-query
description: 当用户要求查询数据库、统计数据、查看表结构、分析业务数据（如"查一下有哪些表""统计最近的订单量"）时使用。先在多数据源中选定目标数据源并使用 read_data_source_spec 读取其使用规范，再通过 list_tables / get_table_info / execute_query 对指定数据源执行只读查询。
---

你拥有多个数据库工具，操作对象是可配置的多个数据源：

- `list_data_sources`：列出可用数据源（id、名称、简要描述）。
- `read_data_source_spec(dataSourceId)`：读取某个数据源的使用规范（表命名约定、字段说明、注意事项）。
- `list_tables(dataSourceId)`：列出指定数据源中所有表及注释。
- `get_table_info(dataSourceId, tableName)`：查看指定数据源中某张表的字段、类型、注释。
- `execute_query(dataSourceId, sql)`：在指定数据源执行一条只读 SQL，返回结果集。

## 查询流程

1. 先调 `list_data_sources` 查看可用数据源，根据名称/描述选定目标数据源 id。
2. 选定数据源后，必须先调 `read_data_source_spec(dataSourceId)` 读取该数据源的使用规范，按规范中的表结构约定与注意事项执行。
3. 不确定有哪些表时，调 `list_tables(dataSourceId)`，根据表名和注释定位目标表。
4. 写 SQL 前必须先调 `get_table_info(dataSourceId, tableName)` 确认字段名和类型，不要凭猜测写字段。
5. 再调 `execute_query(dataSourceId, sql)` 执行查询；如果报错，根据错误信息修正 SQL 后重试。

## SQL 编写规范

1. 只允许只读语句（SELECT / WITH / SHOW / DESC / EXPLAIN），任何写操作（INSERT/UPDATE/DELETE/DDL）都会被拒绝，也不要尝试。
2. 一次只执行一条 SQL，不要拼接多语句。
3. 必须控制返回行数：显式写 `LIMIT`（探索性查询建议 20 以内）；不写 LIMIT 时工具会自动追加 `LIMIT 100`。需要总量时用 `COUNT(*)`，不要把全量数据拉回来数。
4. 表名、字段名用反引号包裹（如 `` `user` ``、`` `order_id` ``），避免与关键字冲突。
5. 统计类查询优先在 SQL 里完成聚合（`COUNT`、`SUM`、`AVG`、`GROUP BY`），只把聚合结果拿回来，而不是拉明细到上下文里再算。
6. 时间范围过滤要明确（如 `WHERE created_at >= '2026-01-01'`），字段类型以 `get_table_info` 返回为准。
7. 查询涉及多张表时用 `JOIN`，子查询语句里的表同样受只读校验约束。
8. 一次查询只针对一个数据源，不要跨数据源 JOIN。
9. 数据必须来自工具真实返回，禁止编造；查询结果为空就如实说明，并给出可能的原因（表名不对、条件太严等）。

## 结果呈现

- 结果集用 Markdown 表格展示，列名翻译成用户能理解的中文。
- 先给结论，再给数据；数据量大时只展示关键行，并说明总行数。
- 用户需要图表时，可配合 data-chart 技能把查询结果可视化。

## 数据导出

- 用户要求导出/下载数据，或明确要 CSV 文件时，同样先选定数据源并 `read_data_source_spec` 读规范，再调用 `export_sql_to_csv(dataSourceId, sql, fileName)`：SQL 必须是只读 SELECT，最多导出 20000 行。
- 工具返回 `downloadUrl` 时，把完整链接以 Markdown 链接形式给用户（如 [下载 CSV](...)），并说明导出了多少行、是否被截断（`truncated`）。
- 导出失败（`success=false`）时按 `error` 信息修正 SQL 重试。