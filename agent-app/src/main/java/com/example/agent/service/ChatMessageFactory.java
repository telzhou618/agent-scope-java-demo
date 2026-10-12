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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 用户消息构建：空消息兜底、附件（图片/文档/文本）转 ContentBlock、/skill:&lt;name&gt; 前缀转技能提示
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageFactory {

    private static final String SKILL_PREFIX = "/skill:";

    private final FileStorageService fileStorageService;
    private final DocumentTextExtractor documentTextExtractor;
    private final ClasspathSkillRepository skillRepository;

    /**
     * 空消息且有附件时替换为兜底文案；既无消息又无附件抛业务异常
     */
    public String effectiveMessage(AgentChatRequest request) {
        boolean blank = request.getMessage() == null || request.getMessage().isBlank();
        boolean hasAttachments = request.getAttachments() != null && !request.getAttachments().isEmpty();
        if (blank && !hasAttachments) {
            throw new BizException("用户消息不能为空");
        }
        return blank ? "请查看我发送的文件" : request.getMessage();
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
     * /skill:&lt;name&gt; 前缀转换为技能使用提示；技能存在时告知模型使用哪个技能，
     * 详情由 Agent 调内置工具 load_skill_through_path 自行加载；非技能消息原样返回
     */
    public String applySkillHint(String message) {
        if (message == null || !message.startsWith(SKILL_PREFIX)) {
            return message;
        }
        String rest = message.substring(SKILL_PREFIX.length());
        int split = -1;
        for (int i = 0; i < rest.length(); i++) {
            if (Character.isWhitespace(rest.charAt(i))) {
                split = i;
                break;
            }
        }
        String name = split < 0 ? rest : rest.substring(0, split);
        String requirement = split < 0 ? "" : rest.substring(split).trim();
        AgentSkill skill = skillRepository.getSkill(name);
        if (skill == null) {
            log.warn("技能不存在，按普通消息处理: {}", name);
            return message;
        }
        return "请使用 " + name + " 技能完成以下需求：" + requirement;
    }
}
