package com.example.agent.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 可查询数据源（供前端 @ 数据源下拉）
 */
@Data
@Builder
public class DatasourceVO {

    private String id;

    private String name;

    private String description;
}