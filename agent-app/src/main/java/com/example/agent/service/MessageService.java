package com.example.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.agent.entity.Message;
import com.example.agent.error.BizException;
import com.example.agent.mapper.MessageMapper;
import com.example.agent.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 消息中心：写入（定时报告、系统通知）与查询（分页/未读数/详情/已读）
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageMapper messageMapper;

    /**
     * 写入一条消息（供定时任务 Job 与系统通知钩子调用）
     */
    public void create(long userId, String type, String sender, String title, String content,
                       Long taskId, String runStatus) {
        Message message = new Message();
        message.setUserId(userId);
        message.setType(type);
        message.setSender(sender == null ? "" : sender);
        message.setTitle(title);
        message.setContent(content);
        message.setIsRead(0);
        message.setTaskId(taskId);
        message.setRunStatus(runStatus);
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);
    }

    /**
     * 分页列表（倒序）：列表不含正文，减少传输
     */
    public Page<MessageVO> page(long userId, long page, long size) {
        Page<Message> entityPage = messageMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getUserId, userId)
                        .orderByDesc(Message::getId));
        Page<MessageVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(m -> toVO(m, false)).toList());
        return voPage;
    }

    public long unreadCount(long userId) {
        Long count = messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getIsRead, 0));
        return count == null ? 0 : count;
    }

    public MessageVO detail(long userId, long id) {
        return toVO(requireOwn(userId, id), true);
    }

    public void markRead(long userId, long id) {
        Message message = requireOwn(userId, id);
        if (message.getIsRead() != null && message.getIsRead() == 1) {
            return;
        }
        Message update = new Message();
        update.setId(id);
        update.setIsRead(1);
        messageMapper.updateById(update);
    }

    private Message requireOwn(long userId, long id) {
        Message message = messageMapper.selectById(id);
        if (message == null || !message.getUserId().equals(userId)) {
            throw new BizException("消息不存在");
        }
        return message;
    }

    private MessageVO toVO(Message message, boolean withContent) {
        return MessageVO.builder()
                .id(message.getId())
                .type(message.getType())
                .sender(message.getSender())
                .title(message.getTitle())
                .content(withContent ? message.getContent() : null)
                .isRead(message.getIsRead() != null && message.getIsRead() == 1)
                .runStatus(message.getRunStatus())
                .createTime(message.getCreateTime())
                .build();
    }
}
