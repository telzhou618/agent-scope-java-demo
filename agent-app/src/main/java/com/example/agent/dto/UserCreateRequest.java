package com.example.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 新增用户请求
 */
@Data
@Schema(description = "新增用户请求")
public class UserCreateRequest {

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名（登录账号）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码（明文，服务端 BCrypt 加密）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @NotBlank(message = "邮箱不能为空")
    @Schema(description = "邮箱", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Schema(description = "昵称，空则取用户名")
    private String nickname;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "状态：1正常 0禁用，空默认 1")
    private Integer status;

    @Schema(description = "是否管理员，空默认 false")
    private Boolean isAdmin;

    @Schema(description = "可用 Agent 名列表，空=无权限；管理员忽略")
    private List<String> agents;
}
