package com.example.agent.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.example.agent.agent.AgentInfo;
import com.example.agent.annotation.OperationLog;
import com.example.agent.dto.AgentChatRequest;
import com.example.agent.dto.AgentSession;
import com.example.agent.dto.CreateSessionRequest;
import com.example.agent.dto.FeedbackRequest;
import com.example.agent.dto.InterruptRequest;
import com.example.agent.dto.PinSessionRequest;
import com.example.agent.dto.Result;
import com.example.agent.dto.SessionIdRequest;
import com.example.agent.service.AgentChatService;
import com.example.agent.service.AgentPermissionService;
import com.example.agent.service.MessageFeedbackService;
import com.example.agent.service.SessionManageService;
import com.example.agent.vo.DatasourceVO;
import com.example.agent.vo.SkillVO;
import io.agentscope.core.message.Msg;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * MCP Client Agent 控制器：只做参数解析与结果包装，业务逻辑在各 Service 中
 */
@RestController
@AllArgsConstructor
@RequestMapping("/agent/scope")
@Tag(name = "AgentScope 智能体")
public class AgentScopeController {

    private final AgentChatService agentChatService;
    private final SessionManageService sessionManageService;
    private final AgentPermissionService agentPermissionService;
    private final MessageFeedbackService feedbackService;

    /**
     * 从 token 解析当前登录用户 id（字符串形式，与会话状态目录名一致）
     */
    private String loginUserId() {
        return String.valueOf(StpUtil.getLoginIdAsLong());
    }

    @PostMapping(value = "/chat_sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式对话")
    public Flux<ServerSentEvent<String>> chatSse(@RequestBody @Validated AgentChatRequest request) {
        return agentChatService.chatSse(StpUtil.getLoginIdAsLong(), request);
    }

    @PostMapping("/createSession")
    @Operation(summary = "创建会话")
    public Result<Void> createSession(@RequestBody @Validated CreateSessionRequest request) {
        sessionManageService.createSession(StpUtil.getLoginIdAsLong(), request);
        return Result.ok();
    }

    @GetMapping("/getMessages")
    @Operation(summary = "历史消息")
    public Result<List<Msg>> getMessages(String sessionId) {
        return Result.okData(sessionManageService.listMessages(loginUserId(), sessionId));
    }

    @GetMapping("/getSessions")
    @Operation(summary = "会话列表")
    public Result<List<AgentSession>> getSessions() {
        return Result.okData(sessionManageService.listSessions(loginUserId()));
    }

    @PostMapping("/delSession")
    @Operation(summary = "删除会话")
    @OperationLog("删除会话")
    public Result<Void> delSessions(@RequestBody @Validated SessionIdRequest request) {
        sessionManageService.deleteSession(StpUtil.getLoginIdAsLong(), request.getSessionId());
        return Result.ok();
    }

    @PostMapping("/pinSession")
    @Operation(summary = "置顶/取消置顶会话")
    @OperationLog("置顶会话")
    public Result<Void> pinSession(@RequestBody @Validated PinSessionRequest request) {
        sessionManageService.pinSession(loginUserId(), request.getSessionId(), request.isPinned());
        return Result.ok();
    }

    @GetMapping("/agents")
    @Operation(summary = "Agent 档位列表（含能力标记与默认档位，按当前用户权限过滤）")
    public Result<List<AgentInfo>> agents() {
        return Result.okData(agentPermissionService.filterAgents(StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/skills")
    @Operation(summary = "技能列表")
    public Result<List<SkillVO>> skills(@RequestParam(required = false) String agentName) {
        return Result.okData(agentChatService.listSkills(StpUtil.getLoginIdAsLong(), agentName));
    }

    @GetMapping("/datasources")
    @Operation(summary = "数据源列表")
    public Result<List<DatasourceVO>> datasources() {
        return Result.okData(agentChatService.listDatasources());
    }

    @PostMapping("/feedback")
    @Operation(summary = "消息反馈（有帮助/没帮助，feedback 为空表示取消）")
    @OperationLog("提交消息反馈")
    public Result<Void> feedback(@RequestBody @Validated FeedbackRequest request) {
        feedbackService.submit(StpUtil.getLoginIdAsLong(),
                request.getSessionId(), request.getMessageId(), request.getFeedback());
        return Result.ok();
    }

    @GetMapping("/getFeedbacks")
    @Operation(summary = "会话的反馈映射（messageId -> up/down）")
    public Result<Map<String, String>> getFeedbacks(String sessionId) {
        return Result.okData(feedbackService.listForSession(StpUtil.getLoginIdAsLong(), sessionId));
    }

    @PostMapping("/interrupt")
    @Operation(summary = "中断会话")
    public Result<Void> interrupt(@RequestBody @Validated InterruptRequest request) {
        agentChatService.interrupt(StpUtil.getLoginIdAsLong(), request);
        return Result.ok();
    }
}