package com.example.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.agentscope.core.state.State;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话元数据，持久化于 agentStateStore 的 session_meta 状态位
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionMeta implements State {

    /**
     * 会话标题
     */
    private String title;

    /**
     * 会话创建时间，格式与 Msg.timestamp 一致（yyyy-MM-dd HH:mm:ss.SSS）
     */
    private String createTime;

    /**
     * 是否置顶
     */
    private boolean pinned;
}
