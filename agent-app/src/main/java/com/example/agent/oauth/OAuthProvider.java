package com.example.agent.oauth;

/**
 * 第三方 OAuth 渠道：新增渠道只需实现本接口并注册为 Spring Bean
 */
public interface OAuthProvider {

    /** 渠道标识 */
    String channel();

    /** 是否启用（读配置，未开启的渠道不注册） */
    boolean enabled();

    /** 拼接跳转授权页的完整 URL（带 state） */
    String authorizeUrl(String state);

    /** 用授权码换取用户信息 */
    OAuthUser resolve(String code);
}