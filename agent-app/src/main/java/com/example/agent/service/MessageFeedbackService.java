package com.example.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.agent.entity.MessageFeedback;
import com.example.agent.mapper.MessageFeedbackMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 消息反馈：按 (userId, sessionId, messageId) 唯一，重复评价覆盖，取消即删除
 */
@Service
@RequiredArgsConstructor
public class MessageFeedbackService {

    private final MessageFeedbackMapper feedbackMapper;

    /**
     * 提交/取消反馈：feedback 为 null 时删除记录（取消），否则存在即更新、不存在则插入
     */
    public void submit(Long userId, String sessionId, String messageId, String feedback) {
        MessageFeedback existing = findOne(userId, sessionId, messageId);
        if (feedback == null) {
            if (existing != null) {
                feedbackMapper.deleteById(existing.getId());
            }
            return;
        }
        if (existing != null) {
            existing.setFeedback(feedback);
            feedbackMapper.updateById(existing);
            return;
        }
        MessageFeedback record = new MessageFeedback();
        record.setUserId(userId);
        record.setSessionId(sessionId);
        record.setMessageId(messageId);
        record.setFeedback(feedback);
        feedbackMapper.insert(record);
    }

    /**
     * 某会话的全部反馈：messageId -> up/down
     */
    public Map<String, String> listForSession(Long userId, String sessionId) {
        Map<String, String> result = new LinkedHashMap<>();
        feedbackMapper.selectList(new LambdaQueryWrapper<MessageFeedback>()
                        .eq(MessageFeedback::getUserId, userId)
                        .eq(MessageFeedback::getSessionId, sessionId))
                .forEach(item -> result.put(item.getMessageId(), item.getFeedback()));
        return result;
    }

    /**
     * 删除某会话的全部反馈（删会话时级联清理）
     */
    public void deleteForSession(Long userId, String sessionId) {
        feedbackMapper.delete(new LambdaQueryWrapper<MessageFeedback>()
                .eq(MessageFeedback::getUserId, userId)
                .eq(MessageFeedback::getSessionId, sessionId));
    }

    private MessageFeedback findOne(Long userId, String sessionId, String messageId) {
        return feedbackMapper.selectOne(new LambdaQueryWrapper<MessageFeedback>()
                .eq(MessageFeedback::getUserId, userId)
                .eq(MessageFeedback::getSessionId, sessionId)
                .eq(MessageFeedback::getMessageId, messageId));
    }
}
