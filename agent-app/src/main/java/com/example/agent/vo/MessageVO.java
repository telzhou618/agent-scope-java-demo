package com.example.agent.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息列表/详情
 */
@Data
@Builder
public class MessageVO {

    private Long id;

    /** report / system */
    private String type;

    /** 发送方 Agent 名，系统消息为空串；前端据此取档位头像与显示名 */
    private String sender;

    private String title;

    /** 正文（Markdown）；列表接口置空，详情接口返回 */
    private String content;

    private Boolean isRead;

    /** success / failed */
    private String runStatus;

    private LocalDateTime createTime;
}
