package com.example.agent.service;

import cn.hutool.core.collection.CollUtil;
import com.example.agent.datasource.MysqlDatasourceRegistry;
import com.example.agent.dto.AgentChatRequest;
import com.example.agent.dto.ChatAttachment;
import com.example.agent.error.BizException;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.URLSource;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用户消息构建：空消息兜底、附件（图片/文档/文本）转 ContentBlock、@技能/@数据源 原位改写
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageFactory {

    private final FileStorageService fileStorageService;
    private final DocumentTextExtractor documentTextExtractor;

    /**
     * 空消息兜底：有附件时替换为兜底文案；消息、附件两者皆空抛业务异常
     */
    public String effectiveMessage(AgentChatRequest request) {
        boolean blank = request.getMessage() == null || request.getMessage().isBlank();
        if (!blank) {
            return request.getMessage();
        }
        if (CollUtil.isEmpty(request.getAttachments())) {
            throw new BizException("用户消息不能为空");
        }
        return "请查看我发送的文件";
    }

    public UserMessage buildUserMessage(String userId, String sessionId, String text,
                                        List<ChatAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return new UserMessage("user", text);
        }
        List<ContentBlock> blocks = new ArrayList<>();
        blocks.add(TextBlock.builder().text(text).build());
        for (ChatAttachment attachment : attachments) {
            if (fileStorageService.isImage(attachment.ext())) {
                // 图片走 OSS 签名地址，不再内嵌 base64
                blocks.add(DataBlock.builder()
                        .source(URLSource.builder()
                                .url(fileStorageService.presignedUrl(userId, sessionId, attachment.id()))
                                .mimeType(fileStorageService.mediaType(attachment.ext()))
                                .build())
                        .build());
                continue;
            }
            byte[] bytes = fileStorageService.readBytes(userId, sessionId, attachment.id());
            if (documentTextExtractor.isDocument(attachment.ext())) {
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
     * 改写用户消息，将来扩展使用
     */
    public String applyMentions(String message) {
        if (!StringUtils.hasText(message)) {
            return message;
        }
        return message;
    }
}