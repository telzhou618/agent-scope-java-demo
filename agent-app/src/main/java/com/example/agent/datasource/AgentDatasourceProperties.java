package com.example.agent.datasource;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * agent.datasources 多数据源配置绑定
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentDatasourceProperties {

    private List<DatasourceConfig> datasources = new ArrayList<>();
}