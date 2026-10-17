package com.example.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.agent.datasource.MysqlDatasourceRegistry;
import com.example.agent.entity.Dashboard;
import com.example.agent.error.BizException;
import com.example.agent.mapper.DashboardMapper;
import com.example.agent.tools.SqlValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据看板：看板 CRUD（按 user_id 隔离）+ 页面取数查询（SELECT-only、≤100 行、≤10s）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** 单次查询最大返回行数 */
    private static final int MAX_ROWS = 100;

    /** MySQL 查询最大执行时间（毫秒） */
    private static final int MAX_EXECUTION_MS = 10_000;

    /** 页面代码体积上限（字符），防止无限膨胀 */
    private static final int MAX_CODE_LENGTH = 200_000;

    private static final Pattern LIMIT_CLAUSE = Pattern.compile("\\blimit\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

    private final DashboardMapper dashboardMapper;
    private final MysqlDatasourceRegistry datasourceRegistry;

    /**
     * 创建看板：校验代码形态 → 生成唯一 dkey（UUID 去横杠）→ 落库
     */
    public Dashboard create(long userId, String name, String description, String code) {
        if (userId <= 0) {
            throw new BizException("无法识别当前用户");
        }
        String validated = validateCode(code);
        Dashboard dashboard = new Dashboard();
        dashboard.setDkey(UUID.randomUUID().toString().replace("-", ""));
        dashboard.setUserId(userId);
        dashboard.setName(StringUtils.hasText(name) ? name.trim() : "未命名看板");
        dashboard.setDescription(description == null ? "" : description);
        dashboard.setCode(validated);
        dashboard.setCreateTime(LocalDateTime.now());
        dashboard.setUpdateTime(LocalDateTime.now());
        dashboardMapper.insert(dashboard);
        log.info("数据看板创建成功, userId={}, key={}", userId, dashboard.getDkey());
        return dashboard;
    }

    /**
     * 当前用户的看板元信息列表（不含页面代码）
     */
    public List<Dashboard> listForUser(long userId) {
        return dashboardMapper.selectList(new LambdaQueryWrapper<Dashboard>()
                .eq(Dashboard::getUserId, userId)
                .orderByDesc(Dashboard::getId)
                .select(Dashboard::getId, Dashboard::getDkey, Dashboard::getUserId,
                        Dashboard::getName, Dashboard::getDescription,
                        Dashboard::getCreateTime, Dashboard::getUpdateTime));
    }

    public Dashboard getOwned(long userId, long id) {
        return requireOwn(userId, id);
    }

    public Dashboard findByKey(String key) {
        return dashboardMapper.selectOne(new LambdaQueryWrapper<Dashboard>()
                .eq(Dashboard::getDkey, key));
    }

    public void updateOwned(long userId, long id, String name, String description, String code) {
        Dashboard dashboard = requireOwn(userId, id);
        if (name != null) {
            dashboard.setName(name.trim());
        }
        if (description != null) {
            dashboard.setDescription(description);
        }
        if (StringUtils.hasText(code)) {
            dashboard.setCode(validateCode(code));
        }
        dashboard.setUpdateTime(LocalDateTime.now());
        dashboardMapper.updateById(dashboard);
    }

    public void deleteOwned(long userId, long id) {
        Dashboard dashboard = requireOwn(userId, id);
        dashboardMapper.deleteById(dashboard.getId());
    }

    /**
     * 页面取数：只读 SQL（SELECT 等）+ 结果集钳制 ≤100 行 + MySQL 优化器提示限制 10s
     */
    public List<Map<String, Object>> executeQuery(String datasourceId, String sql) {
        String validated;
        try {
            validated = SqlValidator.validateReadonlyQuery(sql);
        } catch (IllegalArgumentException e) {
            throw new BizException(e.getMessage());
        }
        String limited = applyRowLimit(validated);
        String hinted = applyExecutionHint(limited);
        return datasourceRegistry.template(datasourceId).queryForList(hinted);
    }

    private Dashboard requireOwn(long userId, long id) {
        Dashboard dashboard = dashboardMapper.selectById(id);
        if (dashboard == null || !dashboard.getUserId().equals(userId)) {
            throw new BizException("数据看板不存在");
        }
        return dashboard;
    }

    private String validateCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException("看板页面代码不能为空");
        }
        if (code.length() > MAX_CODE_LENGTH) {
            throw new BizException("看板页面代码过大（超过 " + MAX_CODE_LENGTH + " 字符）");
        }
        if (!code.toLowerCase().contains("<html")) {
            throw new BizException("看板页面代码必须是完整 HTML（缺少 <html> 标签）");
        }
        return code.trim();
    }

    /**
     * 结果集钳制：显式 LIMIT 超过上限则压到上限；未写 LIMIT 时补默认上限
     */
    private String applyRowLimit(String sql) {
        Matcher matcher = LIMIT_CLAUSE.matcher(sql);
        if (matcher.find()) {
            int limit = Integer.parseInt(matcher.group(1));
            if (limit > MAX_ROWS) {
                return matcher.replaceFirst("LIMIT " + MAX_ROWS);
            }
            return sql;
        }
        return sql + " LIMIT " + MAX_ROWS;
    }

    /**
     * 仅 SELECT 支持优化器提示（MAX_EXECUTION_TIME 必须紧跟 SELECT 之后）
     */
    private String applyExecutionHint(String sql) {
        if (sql.regionMatches(true, 0, "SELECT", 0, 6)) {
            return sql.substring(0, 6) + " /*+ MAX_EXECUTION_TIME(" + MAX_EXECUTION_MS + ") */" + sql.substring(6);
        }
        return sql;
    }
}