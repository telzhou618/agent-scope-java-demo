package com.example.agent.service;

import com.example.agent.entity.TokenUsage;
import com.example.agent.mapper.TokenUsageMapper;
import com.example.agent.vo.RecentRequestVO;
import com.example.agent.vo.UsageBucketRow;
import com.example.agent.vo.UsageSummaryVO;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.model.ChatUsage;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * token 消耗：记录 + 统计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenUsageService {

    private final TokenUsageMapper tokenUsageMapper;

    /**
     * 专门的落库线程：事件线程只负责组装实体，JDBC insert 异步执行，失败仅记日志。
     * 守护线程 + shutdown 保证应用退出时不阻塞。
     */
    private final ExecutorService insertExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "token-usage-insert");
        thread.setDaemon(true);
        return thread;
    });

    @Value("${usage.input-price-per-million}")
    private double inputPricePerMillion;

    @Value("${usage.output-price-per-million}")
    private double outputPricePerMillion;

    /**
     * 记录一次模型调用的 token 消耗，费用按当期单价锁定。
     * 实体在当前线程同步组装（避免跨线程读可变上下文），JDBC insert 提交到专用线程异步执行。
     */
    public void record(RuntimeContext ctx, String agentName, String modelName, ModelCallEndEvent event) {
        ChatUsage usage = event.getUsage();
        if (usage == null) {
            return;
        }
        TokenUsage record = new TokenUsage();
        record.setUserId(Long.valueOf(ctx.getUserId()));
        record.setSessionId(ctx.getSessionId());
        record.setAgentName(agentName);
        record.setModelName(modelName);
        record.setInputTokens(usage.getInputTokens());
        record.setOutputTokens(usage.getOutputTokens());
        record.setCachedTokens(usage.getCachedTokens());
        record.setDurationSeconds(usage.getTime());
        record.setCost(computeCost(usage));
        record.setReplyId(event.getReplyId() == null ? "" : event.getReplyId());
        Object requestIdValue = ctx.get("requestId");
        record.setRequestId(requestIdValue == null ? "" : String.valueOf(requestIdValue));
        insertExecutor.execute(() -> {
            try {
                tokenUsageMapper.insert(record);
            } catch (Exception e) {
                log.warn("token 消耗落库失败, sessionId={}, requestId={}",
                        record.getSessionId(), record.getRequestId(), e);
            }
        });
    }

    @PreDestroy
    void shutdown() {
        insertExecutor.shutdown();
    }

    private BigDecimal computeCost(ChatUsage usage) {
        double input = usage.getInputTokens() / 1_000_000.0 * inputPricePerMillion;
        double output = usage.getOutputTokens() / 1_000_000.0 * outputPricePerMillion;
        return BigDecimal.valueOf(input + output).setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * 最近请求记录：区间内按请求时间倒序取最近 10 条。
     */
    public List<RecentRequestVO> recent(long userId, LocalDate start, LocalDate end) {
        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.plusDays(1).atStartOfDay();
        List<TokenUsage> records = tokenUsageMapper.selectRecent(userId, startTime, endTime);

        List<RecentRequestVO> result = new ArrayList<>(records.size());
        for (TokenUsage record : records) {
            RecentRequestVO vo = new RecentRequestVO();
            vo.setSessionId(record.getSessionId());
            vo.setRequestId(record.getRequestId());
            vo.setModelName(record.getModelName());
            vo.setInputTokens(record.getInputTokens() == null ? 0L : record.getInputTokens().longValue());
            vo.setOutputTokens(record.getOutputTokens() == null ? 0L : record.getOutputTokens().longValue());
            vo.setDurationSeconds(record.getDurationSeconds());
            vo.setCost(record.getCost() == null ? BigDecimal.ZERO : record.getCost());
            vo.setCreateTime(record.getCreateTime());
            result.add(vo);
        }
        return result;
    }

    /**
     * 按区间统计：聚合后把整段区间补零，保证图表形状完整。
     */
    public UsageSummaryVO summary(long userId, LocalDate start, LocalDate end, String granularity) {
        boolean hourly = "hour".equalsIgnoreCase(granularity);
        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.plusDays(1).atStartOfDay();

        List<UsageBucketRow> rows = hourly
                ? tokenUsageMapper.sumByHour(userId, startTime, endTime)
                : tokenUsageMapper.sumByDay(userId, startTime, endTime);

        Map<String, UsageBucketRow> byBucket = new HashMap<>();
        for (UsageBucketRow row : rows) {
            byBucket.put(row.getBucket(), row);
        }

        UsageSummaryVO vo = new UsageSummaryVO();
        UsageSummaryVO.Totals totals = new UsageSummaryVO.Totals();
        totals.setCost(BigDecimal.ZERO);
        totals.setRequests(0L);
        totals.setInputTokens(0L);
        totals.setOutputTokens(0L);
        List<UsageSummaryVO.Bucket> buckets = new ArrayList<>();

        if (hourly) {
            int lastHour = start.equals(LocalDate.now()) ? LocalTime.now().getHour() : 23;
            for (int hour = 0; hour <= lastHour; hour += 1) {
                String key = start.format(DateTimeFormatter.ISO_LOCAL_DATE) + " " + String.format("%02d", hour);
                String label = String.format("%02d:00", hour);
                buckets.add(fill(totals, byBucket.get(key), label));
            }
        } else {
            for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                String key = day.format(DateTimeFormatter.ISO_LOCAL_DATE);
                String label = (day.getMonthValue()) + "/" + day.getDayOfMonth();
                buckets.add(fill(totals, byBucket.get(key), label));
            }
        }

        totals.setTokens(totals.getInputTokens() + totals.getOutputTokens());
        vo.setTotals(totals);
        vo.setBuckets(buckets);
        vo.setDescription(description(start, end, hourly));
        return vo;
    }

    private UsageSummaryVO.Bucket fill(UsageSummaryVO.Totals totals, UsageBucketRow row, String label) {
        UsageSummaryVO.Bucket bucket = new UsageSummaryVO.Bucket();
        bucket.setLabel(label);
        if (row != null) {
            long input = row.getInputTokens() == null ? 0 : row.getInputTokens();
            long output = row.getOutputTokens() == null ? 0 : row.getOutputTokens();
            bucket.setTokens(input + output);
            bucket.setRequests(row.getRequests());
            bucket.setCost(row.getCost());
            totals.setCost(totals.getCost().add(row.getCost()));
            totals.setRequests(totals.getRequests() + row.getRequests());
            totals.setInputTokens(totals.getInputTokens() + input);
            totals.setOutputTokens(totals.getOutputTokens() + output);
        } else {
            bucket.setTokens(0L);
            bucket.setRequests(0L);
            bucket.setCost(BigDecimal.ZERO);
        }
        return bucket;
    }

    private String description(LocalDate start, LocalDate end, boolean hourly) {
        String range = start.equals(end)
                ? formatDay(start)
                : formatDay(start) + " – " + formatDay(end);
        return range + " · " + (hourly ? "按小时" : "按天");
    }

    private String formatDay(LocalDate date) {
        return date.getMonthValue() + " 月 " + date.getDayOfMonth() + " 日";
    }
}
