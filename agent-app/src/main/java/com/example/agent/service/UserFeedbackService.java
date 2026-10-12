package com.example.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.agent.dto.FeedbackStatusRequest;
import com.example.agent.dto.UserFeedbackRequest;
import com.example.agent.entity.Message;
import com.example.agent.entity.User;
import com.example.agent.entity.UserFeedback;
import com.example.agent.error.BizException;
import com.example.agent.mapper.UserFeedbackMapper;
import com.example.agent.mapper.UserMapper;
import com.example.agent.vo.UserFeedbackVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户反馈：提交、管理员分页查询与处理；标记已处理时给提交人发系统通知
 */
@Service
@RequiredArgsConstructor
public class UserFeedbackService {

    private final UserFeedbackMapper feedbackMapper;
    private final UserMapper userMapper;
    private final MessageService messageService;

    public void submit(Long userId, UserFeedbackRequest request) {
        UserFeedback record = new UserFeedback();
        record.setUserId(userId);
        record.setType(request.getType() == null ? "idea" : request.getType());
        record.setContent(request.getContent());
        record.setContact(request.getContact() == null ? "" : request.getContact());
        feedbackMapper.insert(record);
    }

    /**
     * 意见反馈分页列表：反馈内容/联系方式任一模糊命中；批量查提交人回填 username（用户已删除时填空串）
     */
    public Page<UserFeedbackVO> page(long page, long size, Integer status, String keyword) {
        LambdaQueryWrapper<UserFeedback> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(UserFeedback::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(UserFeedback::getContent, keyword)
                    .or().like(UserFeedback::getContact, keyword));
        }
        wrapper.orderByDesc(UserFeedback::getId);
        Page<UserFeedback> feedbackPage = feedbackMapper.selectPage(new Page<>(page, size), wrapper);

        List<Long> userIds = feedbackPage.getRecords().stream()
                .map(UserFeedback::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, User> userMap = userIds.isEmpty()
                ? Map.of()
                : userMapper.selectByIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        Page<UserFeedbackVO> voPage = new Page<>(feedbackPage.getCurrent(), feedbackPage.getSize(), feedbackPage.getTotal());
        voPage.setRecords(feedbackPage.getRecords().stream().map(feedback -> {
            UserFeedbackVO vo = UserFeedbackVO.from(feedback);
            User submitter = userMap.get(feedback.getUserId());
            vo.setUsername(submitter == null ? "" : submitter.getUsername());
            return vo;
        }).toList());
        return voPage;
    }

    public void updateStatus(FeedbackStatusRequest request) {
        UserFeedback existing = feedbackMapper.selectById(request.getId());
        if (existing == null) {
            throw new BizException("反馈不存在");
        }
        UserFeedback update = new UserFeedback();
        update.setId(request.getId());
        update.setStatus(request.getStatus());
        update.setUpdateTime(LocalDateTime.now());
        feedbackMapper.updateById(update);
        // 状态变为已处理且管理员勾选「通知用户」时，给提交人发系统通知（已是已处理的重复设置不重复发）；
        // 处理结果携带在消息内容中（选填）
        boolean nowDone = request.getStatus() != null && request.getStatus() == 1;
        boolean wasDone = existing.getStatus() != null && existing.getStatus() == 1;
        if (nowDone && !wasDone && Boolean.TRUE.equals(request.getNotify()) && existing.getUserId() != null) {
            StringBuilder content = new StringBuilder("你提交的意见反馈已被管理员标记为「已处理」，感谢你的建议！");
            if (StringUtils.hasText(request.getResult())) {
                // 加粗收尾紧跟中文冒号会违反 CommonMark 定界规则导致不渲染，冒号放加粗外
                content.append("\n\n**处理结果**：").append(request.getResult().trim());
            }
            content.append("\n\n> ").append(existing.getContent());
            messageService.create(existing.getUserId(), Message.TYPE_SYSTEM, "",
                    "你提交的意见反馈已处理", content.toString(), null, "success");
        }
    }
}
