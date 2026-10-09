package com.example.agent.vo;

import com.example.agent.entity.User;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户管理列表/详情返回给前端的用户信息（不含密码）
 */
@Data
public class UserManageVO {

    private Long id;

    private String username;

    private String nickname;

    private String email;

    private String avatar;

    /**
     * 状态：1 正常，0 禁用
     */
    private Integer status;

    /**
     * 是否管理员
     */
    private Boolean isAdmin;

    /**
     * 可用 Agent 列表（管理员不受限，仍按库中存储返回）
     */
    private List<String> agents;

    private String createdBy;

    private String updatedBy;

    private LocalDateTime registerTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static UserManageVO from(User user) {
        UserManageVO vo = new UserManageVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setIsAdmin(user.getIsAdmin() != null && user.getIsAdmin() == 1);
        vo.setAgents(UserVO.splitAgents(user.getAgents()));
        vo.setCreatedBy(user.getCreatedBy());
        vo.setUpdatedBy(user.getUpdatedBy());
        vo.setRegisterTime(user.getRegisterTime());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
    }
}
