package com.example.agent.vo;

import com.example.agent.entity.User;
import lombok.Data;

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

    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        return vo;
    }
}
