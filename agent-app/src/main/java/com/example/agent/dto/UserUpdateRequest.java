package com.example.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 编辑用户请求：仅更新非空字段
 */
@Data
@Schema(description = "编辑用户请求")
public class UserUpdateRequest {

    @NotNull(message = "用户ID不能为空")
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "状态：1正常 0禁用")
    private Integer status;

    @Schema(description = "是否管理员")
    private Boolean isAdmin;

    @Schema(description = "可用 Agent 名列表，空=无权限；管理员强制为空")
    private List<String> agents;

    @Schema(description = "新密码（明文），不填则不修改")
    private String password;
}
