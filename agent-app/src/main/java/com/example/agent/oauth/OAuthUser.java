package com.example.agent.oauth;

import lombok.Builder;
import lombok.Data;

/**
 * 渠道授权后得到的统一用户信息
 */
@Data
@Builder
public class OAuthUser {

    private String channel;

    /** 渠道侧用户唯一标识 */
    private String openId;

    /** 昵称/登录名 */
    private String login;

    private String avatar;

    private String email;
}