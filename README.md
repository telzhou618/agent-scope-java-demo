# agent-scope-java-demo

Agent + MCP 演示工程：基于 [AgentScope Java](https://github.com/agentscope-ai/agentscope-java) Harness 构建智能体服务，通过 MCP（Streamable HTTP）调用工具服务。

## 模块说明

| 模块 | 端口 | 说明 |
| --- | --- | --- |
| `mcp-server` | 8081 | 基于 Spring AI MCP Server（STREAMABLE 协议），提供示例工具：当前时间、模拟天气查询 |
| `agent-app` | 8082 | 基于 AgentScope HarnessAgent 的智能体服务，连接 MCP server 获取工具，对外提供 SSE 流式对话接口 |

## 环境要求

- JDK 17+
- Maven 3.6+
- DashScope API Key（环境变量 `YOKA_DASHSCOPE_API_KEY`）

## 启动方式

```bash
# 1. 编译
mvn clean compile

# 2. 先启动 MCP server（8081）
mvn -pl mcp-server spring-boot:run

# 3. 再启动 Agent 服务（8082）
export YOKA_DASHSCOPE_API_KEY=sk-xxxx
mvn -pl agent-app spring-boot:run
```

可选环境变量：

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `YOKA_DASHSCOPE_API_KEY` | 无（必填） | DashScope API Key |
| `MCP_SERVER_TOKEN` | 空 | MCP server 访问令牌，为空时不附加 Authorization 头 |

## 接口示例

流式对话（SSE）：

```bash
curl -N -X POST http://localhost:8082/agent/scope/chat_sse \
  -H "Content-Type: application/json" \
  -d '{"userId":"u1","sessionId":"s1","message":"现在几点"}'
```

历史消息：`GET /agent/scope/getMessages?userId=u1&sessionId=s1`

会话列表：`GET /agent/scope/getSessions?userId=u1`

API 文档：启动 agent-app 后访问 `http://localhost:8082/doc.html`（Knife4j）。

## 说明

- 智能体状态与上下文默认存储在 `~/.agentscope` 目录下（JsonFileAgentStateStore）。
- `AgentConfig` 中工具权限模式为 `PermissionMode.BYPASS`（不校验权限），仅供演示，生产环境请勿使用。
