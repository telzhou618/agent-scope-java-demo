package com.example.agent.service;

import com.example.agent.agent.AgentInfo;
import com.example.agent.agent.AgentRegistry;
import com.example.agent.entity.User;
import com.example.agent.error.BizException;
import com.example.agent.error.ErrorCode;
import com.example.agent.mapper.UserMapper;
import com.example.agent.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Agent 使用权限：管理员放行全部，普通用户按 t_user.agents 白名单控制
 */
@Service
@RequiredArgsConstructor
public class AgentPermissionService {

    private final UserMapper userMapper;
    private final AgentRegistry agentRegistry;

    /**
     * 校验当前用户是否可使用指定 Agent；未知名称沿用 registry 的原有异常
     */
    public void checkAgent(long userId, String agentName) {
        // 先让 registry 校验名称，未知名称抛“未知 Agent”业务异常
        agentRegistry.getAgent(agentName);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户不存在或已被删除");
        }
        if (user.getIsAdmin() != null && user.getIsAdmin() == 1) {
            return;
        }
        if (!UserVO.splitAgents(user.getAgents()).contains(agentName)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无该 Agent 使用权限");
        }
    }

    /**
     * 当前用户可见的 Agent 档位列表；普通用户无白名单时返回空表（不回落默认档）
     */
    public List<AgentInfo> filterAgents(long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户不存在或已被删除");
        }
        if (user.getIsAdmin() != null && user.getIsAdmin() == 1) {
            return agentRegistry.listAgents();
        }
        Set<String> allowed = new HashSet<>(UserVO.splitAgents(user.getAgents()));
        return agentRegistry.listAgents().stream()
                .filter(info -> allowed.contains(info.getName()))
                .toList();
    }
}
