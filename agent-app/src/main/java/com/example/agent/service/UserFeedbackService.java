package com.example.agent.service;

import com.example.agent.dto.UserFeedbackRequest;
import com.example.agent.entity.UserFeedback;
import com.example.agent.mapper.UserFeedbackMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户反馈
 */
@Service
@RequiredArgsConstructor
public class UserFeedbackService {

    private final UserFeedbackMapper feedbackMapper;

    public void submit(Long userId, UserFeedbackRequest request) {
        UserFeedback record = new UserFeedback();
        record.setUserId(userId);
        record.setType(request.getType() == null ? "idea" : request.getType());
        record.setContent(request.getContent());
        record.setContact(request.getContact() == null ? "" : request.getContact());
        feedbackMapper.insert(record);
    }
}
