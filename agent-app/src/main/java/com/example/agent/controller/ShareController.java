package com.example.agent.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.example.agent.dto.Result;
import com.example.agent.error.BizException;
import com.example.agent.service.ShareService;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.Msg;
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
    @Operation(summary = "分享的会话消息（UUID 短链，7 天内免登录；图片/附件等内容块不下发）")
    public Result<List<Msg>> sharedSession(@RequestParam String t) {
        String[] resolved = shareService.resolveSession(t);
        AgentState state = agentStateStore.get(resolved[0], resolved[1], "agent_state", AgentState.class)
                .orElseThrow(() -> new BizException("分享内容已失效"));
        return Result.okData(state.getContext().stream()
                .map(ShareController::stripBinaryBlocks)
                .toList());
    }

    /**
     * 去掉含文件内容的块（图片 base64 等 DataBlock），分享页只读文字
     */
    private static Msg stripBinaryBlocks(Msg msg) {
        List<ContentBlock> content = msg.getContent();
        if (content == null || content.stream().noneMatch(b -> b instanceof DataBlock)) {
            return msg;
        }
        return Msg.builder()
                .id(msg.getId())
                .role(msg.getRole())
                .name(msg.getName())
                .content(content.stream().filter(b -> !(b instanceof DataBlock)).toList())
                .timestamp(msg.getTimestamp())
                .metadata(msg.getMetadata())
                .build();
    }
}
