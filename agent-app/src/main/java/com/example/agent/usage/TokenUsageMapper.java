package com.example.agent.usage;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface TokenUsageMapper extends BaseMapper<TokenUsage> {

    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS bucket, " +
            "SUM(input_tokens) AS inputTokens, SUM(output_tokens) AS outputTokens, " +
            "SUM(cost) AS cost, COUNT(*) AS requests " +
            "FROM t_token_usage " +
            "WHERE user_id = #{userId} AND create_time >= #{start} AND create_time < #{end} " +
            "GROUP BY bucket ORDER BY bucket")
    List<UsageBucketRow> sumByDay(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d %H') AS bucket, " +
            "SUM(input_tokens) AS inputTokens, SUM(output_tokens) AS outputTokens, " +
            "SUM(cost) AS cost, COUNT(*) AS requests " +
            "FROM t_token_usage " +
            "WHERE user_id = #{userId} AND create_time >= #{start} AND create_time < #{end} " +
            "GROUP BY bucket ORDER BY bucket")
    List<UsageBucketRow> sumByHour(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
