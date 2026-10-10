package com.example.agent.tools;

import java.util.regex.Pattern;

/**
 * SQL 安全校验：黑名单关键字 + 仅允许只读语句 + 禁止多语句
 */
public final class SqlValidator {

    private static final Pattern DANGEROUS_KEYWORDS = Pattern.compile(
            "\\b(DROP|ALTER|TRUNCATE|DELETE|UPDATE|INSERT|CREATE|EXEC|EXECUTE|GRANT|REVOKE|REPLACE|MERGE|CALL|LOCK|UNLOCK|SET|USE|KILL|LOAD|OUTFILE|DUMPFILE)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern READONLY_PREFIX = Pattern.compile(
            "^(SELECT|WITH|SHOW|DESC|DESCRIBE|EXPLAIN)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_$]*");

    private SqlValidator() {
    }

    public static boolean isDangerousSql(String sql) {
        return sql != null && DANGEROUS_KEYWORDS.matcher(sql).find();
    }

    /**
     * 校验表名是合法标识符，防止 SHOW FULL COLUMNS FROM 拼接注入
     */
    public static boolean isValidIdentifier(String name) {
        return name != null && IDENTIFIER.matcher(name).matches();
    }

    /**
     * 校验是否为可执行的只读查询：只允许 SELECT/WITH/SHOW/DESC/EXPLAIN 开头，禁止多语句
     *
     * @return 去掉首尾空白和末尾分号的 SQL
     * @throws IllegalArgumentException 校验不通过时抛出
     */
    public static String validateReadonlyQuery(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("安全限制：SQL 不能为空");
        }
        String normalized = sql.trim();
        if (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        if (normalized.contains(";")) {
            throw new IllegalArgumentException("安全限制：不允许多语句执行");
        }
        if (!READONLY_PREFIX.matcher(normalized).find()) {
            throw new IllegalArgumentException("安全限制：仅允许执行 SELECT/WITH/SHOW/DESC/EXPLAIN 只读语句");
        }
        if (isDangerousSql(normalized)) {
            throw new IllegalArgumentException("安全限制：SQL 包含危险关键字，仅允许只读查询");
        }
        return normalized;
    }
}
