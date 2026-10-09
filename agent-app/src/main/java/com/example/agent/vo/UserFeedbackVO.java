package com.example.agent.vo;

import com.example.agent.entity.UserFeedback;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 返回给前端的用户反馈（附带提交人用户名）
 */
@Data
public class UserFeedbackVO {

    private Long id;

    private Long userId;

    /** bug 问题 / idea 建议 / other 其他 */
    private String type;

    private String content;

    private String contact;

    /** 0 待处理 / 1 已处理 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 提交人用户名，用户不存在时为空串
     */
    private String username;

    public static UserFeedbackVO from(UserFeedback feedback) {
        UserFeedbackVO vo = new UserFeedbackVO();
        vo.setId(feedback.getId());
        vo.setUserId(feedback.getUserId());
        vo.setType(feedback.getType());
        vo.setContent(feedback.getContent());
        vo.setContact(feedback.getContact());
        vo.setStatus(feedback.getStatus());
        vo.setCreateTime(feedback.getCreateTime());
        vo.setUpdateTime(feedback.getUpdateTime());
        return vo;
    }
}
