package com.example.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "技能信息")
public class SkillVO {

    @Schema(description = "技能名")
    private String name;

    @Schema(description = "技能描述")
    private String description;
}
