package com.example.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 修改个人资料请求：字段均可空，仅更新非空字段（空串允许更新）
 */
@Data
@Schema(description = "修改个人资料请求")
public class ProfileUpdateRequest {

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像地址")
    private String avatar;
}
