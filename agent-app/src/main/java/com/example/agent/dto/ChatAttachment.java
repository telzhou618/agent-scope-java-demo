package com.example.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "聊天附件")
public record ChatAttachment(
        @Schema(description = "文件ID（uuid.ext）", requiredMode = Schema.RequiredMode.REQUIRED)
        String id,

        @Schema(description = "原始文件名", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(description = "扩展名（小写、不含点）", requiredMode = Schema.RequiredMode.REQUIRED)
        String ext,

        @Schema(description = "文件大小（字节）", requiredMode = Schema.RequiredMode.REQUIRED)
        long size) {

}
