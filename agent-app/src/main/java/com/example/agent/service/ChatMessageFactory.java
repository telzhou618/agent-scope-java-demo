package com.example.agent.service;

import com.example.agent.dto.AgentChatRequest;
import com.example.agent.dto.ChatAttachment;
import com.example.agent.error.BizException;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 用户消息构建：空消息兜底、附件（图片/文档/文本）转 ContentBlock、技能名转技能提示
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageFactory {

    private final FileStorageService fileStorageService;
    private final DocumentTextExtractor documentTextExtractor;
    private final ClasspathSkillRepository skillRepository;

    /**
     * 空消息兜底：有附件时替换为兜底文案，有技能时返回空串（技能提示即为内容）；
     * 消息、附件、技能三者皆空抛业务异常
     */
    public String effectiveMessage(AgentChatRequest request) {
        boolean blank = request.getMessage() == null || request.getMessage().isBlank();
        if (!blank) {
            return request.getMessage();
        }
        boolean hasAttachments = request.getAttachments() != null && !request.getAttachments().isEmpty();
        boolean hasSkill = StringUtils.hasText(request.getSkill());
        if (!hasAttachments && !hasSkill) {
            throw new BizException("用户消息不能为空");
        }
        return hasSkill ? "" : "请查看我发送的文件";
    }

    public UserMessage buildUserMessage(String userId, String sessionId, String text,
                                        List<ChatAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return new UserMessage("user", text);
        }
        List<ContentBlock> blocks = new ArrayList<>();
        blocks.add(TextBlock.builder().text(text).build());
        for (ChatAttachment attachment : attachments) {
            byte[] bytes = fileStorageService.readBytes(userId, sessionId, attachment.id());
            if (fileStorageService.isImage(attachment.ext())) {
                // DataBlock 是官方推荐的多模态块（统一替代旧版 ImageBlock）
                blocks.add(DataBlock.builder()
                        .source(Base64Source.builder()
                                .mediaType(fileStorageService.mediaType(attachment.ext()))
                                .data(Base64.getEncoder().encodeToString(bytes))
                                .build())
                        .build());
            } else if (documentTextExtractor.isDocument(attachment.ext())) {
                blocks.add(TextBlock.builder().text(attachmentText(attachment.name(),
                        documentTextExtractor.extract(attachment.name(), attachment.ext(), bytes))).build());
            } else {
                blocks.add(TextBlock.builder().text(attachmentText(attachment.name(),
                        new String(bytes, StandardCharsets.UTF_8))).build());
            }
        }
        return new UserMessage("user", blocks.toArray(new ContentBlock[0]));
    }

    private String attachmentText(String name, String content) {
        return "【附件：" + name + "】\n" + content;
    }

    /**
     * 技能名转换为技能使用提示：告知模型使用哪个技能，
     * 详情由 Agent 调内置工具 load_skill_through_path 自行加载；无技能时原样返回
     */
    public String applySkillHint(String skill, String message) {
        if (!StringUtils.hasText(skill)) {
            return message;
        }
        AgentSkill agentSkill = skillRepository.getSkill(skill);
        if (agentSkill == null) {
            log.warn("技能不存在: {}", skill);
            throw new BizException("技能不存在: " + skill);
        }
        return StringUtils.hasText(message)
                ? "请使用 " + skill + " 技能完成以下需求：" + message
                : "请使用 " + skill + " 技能";
    }
}
