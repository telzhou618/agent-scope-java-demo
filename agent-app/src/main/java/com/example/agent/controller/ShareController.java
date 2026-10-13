package com.example.agent.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.example.agent.dto.Result;
import com.example.agent.error.BizException;
import com.example.agent.service.ShareService;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会话分享：生成短链 + 免登录只读访问（附件/图片内容块不下发，禁止下载文件）
 */
@RestController
@AllArgsConstructor
@RequestMapping("/agent/scope/share")
@Tag(name = "会话分享")
public class ShareController {

    private final ShareService shareService;
    private final AgentStateStore agentStateStore;

    @GetMapping("/sessionLink")
    @Operation(summary = "生成会话分享链接（7 天有效，免登录可访问）")
    public Result<String> sessionLink(@RequestParam String sessionId) {
        String userId = String.valueOf(StpUtil.getLoginIdAsLong());
        // 只能分享自己的会话
        if (!agentStateStore.listSessionIds(userId).contains(sessionId)) {
            throw new BizException("会话不存在");
        }
        return Result.okData(shareService.sessionSharePath(sessionId, StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/session")
    @Operation(summary = "分享的会话消息（UUID 短链，7 天内免登录；图片/附件、工具调用与思考过程等内容块不下发）")
    public Result<List<Msg>> sharedSession(@RequestParam String t) {
        String[] resolved = shareService.resolveSession(t);
        AgentState state = agentStateStore.get(resolved[0], resolved[1], "agent_state", AgentState.class)
                .orElseThrow(() -> new BizException("分享内容已失效"));
        return Result.okData(state.getContext().stream()
                .map(ShareController::stripPrivateBlocks)
                .toList());
    }

    /**
     * 分享页只读正文：过滤图片/附件（DataBlock）、工具调用（ToolUseBlock/ToolResultBlock）与思考过程（ThinkingBlock）
     */
    private static Msg stripPrivateBlocks(Msg msg) {
        List<ContentBlock> content = msg.getContent();
        if (content == null || content.stream().noneMatch(ShareController::isPrivateBlock)) {
            return msg;
        }
        return Msg.builder()
                .id(msg.getId())
                .role(msg.getRole())
                .name(msg.getName())
                .content(content.stream().filter(b -> !isPrivateBlock(b)).toList())
                .timestamp(msg.getTimestamp())
                .metadata(msg.getMetadata())
                .build();
    }

    private static boolean isPrivateBlock(ContentBlock block) {
        return block instanceof DataBlock
                || block instanceof ThinkingBlock
                || block instanceof ToolUseBlock
                || block instanceof ToolResultBlock;
    }
}
