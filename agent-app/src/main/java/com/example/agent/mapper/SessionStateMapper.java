package com.example.agent.mapper;

import com.example.agent.vo.SessionStateRow;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * agentscope_sessions 状态表只读批量查询（会话列表去 N+1 用），
 * SQL 写在 resources/mapper/SessionStateMapper.xml
 */
public interface SessionStateMapper {

    /**
     * 批量取某用户全部会话的 session_meta（小 JSON），session_id 列带 userId 前缀
     */
    List<SessionStateRow> listSessionMetas(@Param("userId") String userId);

    /**
     * 批量取指定会话的 agent_state（仅给没有 meta 的老会话回退摘要用），入参为带 userId 前缀的 slotId
     */
    List<SessionStateRow> listAgentStates(@Param("slotIds") Collection<String> slotIds);
}
