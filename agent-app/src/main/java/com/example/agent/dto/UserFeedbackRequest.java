package com.example.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户反馈提交请求")
public class UserFeedbackRequest {

    @Pattern(regexp = "^(bug|idea|other)$", message = "反馈类型不合法")
    @Schema(description = "类型：bug问题 idea建议 other其他，默认 idea")
    private String type;

    @NotBlank(message = "反馈内容不能为空")
    @Size(max = 500, message = "反馈内容最多 500 字")
    @Schema(description = "反馈内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Size(max = 100, message = "联系方式最多 100 字")
    @Schema(description = "联系方式（选填）")
    private String contact;
}
