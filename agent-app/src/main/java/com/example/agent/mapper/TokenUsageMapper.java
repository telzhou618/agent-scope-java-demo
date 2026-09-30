package com.example.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.agent.entity.TokenUsage;
import com.example.agent.vo.UsageBucketRow;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * token 消耗统计 Mapper，SQL 写在 resources/mapper/TokenUsageMapper.xml
 */
public interface TokenUsageMapper extends BaseMapper<TokenUsage> {

    List<UsageBucketRow> sumByDay(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    List<UsageBucketRow> sumByHour(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
