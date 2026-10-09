package com.example.agent.config;

import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.io.IOException;

/**
 * Agent 基础组件装配：状态存储、技能市场、标题模型。
 * 三档 HarnessAgent 由 com.example.agent.agent 包下的工厂与注册表构建
 */
@Slf4j
@Configuration
public class AgentConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    @Autowired
    private DataSource dataSource;


    @Bean
    public AgentStateStore agentStateStore() {
        // 会话状态落库（agent_demo.agentscope_sessions），建表语句见根目录 sql/init.sql
        return new MysqlAgentStateStore(dataSource, "agent_demo", "agentscope_sessions", false);
    }

    /**
     * Classpath 技能市场：扫描 resources/skills/<name>/SKILL.md，注册后 Agent 每轮推理
     * 自动可见技能清单，并通过内置工具 load_skill_through_path 自行加载技能详情
     */
    @Bean
    public ClasspathSkillRepository classpathSkillRepository() throws IOException {
        return new ClasspathSkillRepository("skills");
    }

    @Bean
    public DashScopeChatModel titleModel() {
        // 标题生成模型，与压缩模型共用轻量级 flash；非流式整体返回，配合 blockLast 收敛全文
        return buildFlashModel(false);
    }

    /**
     * 构建轻量级 flash 模型（qwen-flash），供标题生成等辅助任务使用
     *
     * @param stream 是否流式：标题生成需要完整文本走非流式
     */
    private DashScopeChatModel buildFlashModel(boolean stream) {
        return DashScopeChatModel.builder()
                .apiKey(dashScopeApiKey)
                .modelName("qwen-flash")
                .stream(stream)
                .formatter(new DashScopeChatFormatter())
                .build();
    }
}
