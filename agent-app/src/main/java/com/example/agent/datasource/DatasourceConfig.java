package com.example.agent.datasource;

import lombok.Data;

/**
 * 单个可查询数据源配置（对应 application.yaml 里 agent.datasources[] 的一项）
 */
@Data
public class DatasourceConfig {

    /** 数据源唯一标识，工具入参用它 */
    private String id;

    /** 名称 */
    private String name;

    /** 简要描述 */
    private String description;

    /** JDBC 连接地址 */
    private String url;

    /** 用户名 */
    private String username;

    /** 密码（用环境变量占位） */
    private String password;

    /** 驱动类，默认 MySQL */
    private String driverClassName = "com.mysql.cj.jdbc.Driver";
}