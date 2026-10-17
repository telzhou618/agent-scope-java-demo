package com.example.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QueryRequest {

    @NotBlank(message = "数据源不能为空")
    private String datasourceId;

    @NotBlank(message = "SQL 不能为空")
    private String sql;
}