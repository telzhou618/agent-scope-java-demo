package com.example.agent;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 启动完整上下文依赖外部环境，默认禁用：
 * 1. mcp-server 需先启动（默认 http://localhost:8081/mcp）
 * 2. 需配置环境变量 YOKA_DASHSCOPE_API_KEY
 * 满足条件后可移除 @Disabled 手动执行。
 */
@Disabled("依赖运行中的 mcp-server 和 DashScope API Key，仅手动执行")
@SpringBootTest
class AgentAppApplicationTests {

    @Test
    void contextLoads() {
    }

}
