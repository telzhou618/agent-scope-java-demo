package com.example.agent.vo;

import com.example.agent.entity.User;
import com.example.agent.enums.UserPermission;
import lombok.Data;

import java.util.Arrays;
import java.util.List;

/**
 * 返回给前端的用户信息（不含密码）
 */
@Data
public class UserVO {

    private Long id;

    private String username;

    private String nickname;

    private String email;

    private String avatar;

    /**
     * 是否管理员
     */
    private Boolean isAdmin;

    /**
     * 可用 Agent 列表（管理员不受限，仍按库中存储返回）
     */
    private List<String> agents;

    /**
     * 权限码列表（管理员不受限，仍按库中存储返回）
     */
    private List<String> permissions;

    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setIsAdmin(user.getIsAdmin() != null && user.getIsAdmin() == 1);
        vo.setAgents(splitAgents(user.getAgents()));
        vo.setPermissions(UserPermission.parse(user.getPermissions()));
        return vo;
    }

    /**
     * 逗号分隔的 Agent 串拆为列表，空串返回空表
     */
    public static List<String> splitAgents(String agents) {
        if (agents == null || agents.isBlank()) {
            return List.of();
        }
        return Arrays.stream(agents.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
