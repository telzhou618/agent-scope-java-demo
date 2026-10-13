package com.example.agent.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 拦截器：除登录/退出与接口文档外，所有接口都要求登录
 */
@Configuration
@MapperScan("com.example.agent.mapper")
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/logout",
                        // 导出文件下载走 URL 签名校验，不要求登录态
                        "/agent/scope/files/download/**",
                        // 消息分享链接走 URL 签名校验，不要求登录态
                        "/agent/scope/message/shared",
                        // 会话分享链接走 token 校验，不要求登录态
                        "/agent/scope/share/session",
                        // 第三方 OAuth 登录流程免登录（providers/authorize/callback）
                        "/auth/oauth/**",
                        "/error",
                        "/doc.html",
                        "/v3/api-docs/**",
                        "/swagger-resources/**",
                        "/webjars/**",
                        "/favicon.ico"
                );
    }
}
