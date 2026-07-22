# agent-scope-java-demo

Agent + MCP 演示工程：基于 [AgentScope Java](https://github.com/agentscope-ai/agentscope-java) Harness 构建智能体服务，通过 MCP（Streamable HTTP）调用工具服务。

![img.png](imgs/img.png)

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
  -d '{"userId":"1","sessionId":"11","message":"查询一下杭州今天的天气"}'
```
返回结果:
``` text
data:{"content":"","type":"agent_start"}

data:{"content":"The","type":"thinking"}

data:{"content":" user wants to check","type":"thinking"}

data:{"content":" the weather in Hang","type":"thinking"}

data:{"content":"zhou today","type":"thinking"}

data:{"content":". Let me first","type":"thinking"}

data:{"content":" get the","type":"thinking"}

data:{"content":" current date/time and","type":"thinking"}

data:{"content":" then query the weather","type":"thinking"}

data:{"content":" for Hangzhou.","type":"thinking"}

data:{"toolCall":{"toolCallId":"call_fff3b2bcfc62485485078ba0","toolName":"get_current_datetime","toolParams":"{}"},"type":"tool_call"}

data:{"toolCall":{"toolCallId":"call_fe8265ffb6554f449ea452c8","toolName":"queryWeather","toolParams":"{\"city\": \"杭州\"}"},"type":"tool_call"}

data:{"toolCall":{"toolCallId":"call_fff3b2bcfc62485485078ba0","toolName":"get_current_datetime","toolResults":"2026-07-22 17:40:07"},"type":"tool_result"}

data:{"toolCall":{"toolCallId":"call_fe8265ffb6554f449ea452c8","toolName":"queryWeather","toolResults":"城市杭州天气信息，温度为25度，湿度为60%，天气状况为晴天。"},"type":"tool_result"}

data:{"content":"The","type":"thinking"}

data:{"content":" user asked","type":"thinking"}

data:{"content":" about the weather","type":"thinking"}

data:{"content":" in Hangzhou today","type":"thinking"}

data:{"content":". I have the","type":"thinking"}

data:{"content":" results now.\n","type":"thinking"}

data:{"content":"以下是杭州今天（","type":"text_block"}

data:{"content":"202","type":"text_block"}

data:{"content":"6年7月","type":"text_block"}

data:{"content":"22日","type":"text_block"}

data:{"content":"）的天气信息：","type":"text_block"}

data:{"content":"\n\n|","type":"text_block"}

data:{"content":" 项目 | ","type":"text_block"}

data:{"content":"详情","type":"text_block"}

data:{"content":" |\n|------","type":"text_block"}

data:{"content":"|------|\n","type":"text_block"}

data:{"content":"| 🌡","type":"text_block"}

data:{"content":"️ 温度 |","type":"text_block"}

data:{"content":" **","type":"text_block"}

data:{"content":"25°C**","type":"text_block"}

data:{"content":" |\n|","type":"text_block"}

data:{"content":" 💧 湿度 |","type":"text_block"}

data:{"content":" **60%**","type":"text_block"}

data:{"content":" |\n|","type":"text_block"}

data:{"content":" ☀️ 天气","type":"text_block"}

data:{"content":"状况 | **","type":"text_block"}

data:{"content":"晴天** |\n\n","type":"text_block"}

data:{"content":"今天杭州天气晴朗","type":"text_block"}

data:{"content":"，温度","type":"text_block"}

data:{"content":"适宜，非常适合外出","type":"text_block"}

data:{"content":"活动！😊","type":"text_block"}

data:{"content":"[io.agentscope.core.message.ThinkingBlock@12ed8199, 以下是杭州今天（2026年7月22日）的天气信息：\n\n| 项目 | 详情 |\n|------|------|\n| 🌡️ 温度 | **25°C** |\n| 💧 湿度 | **60%** |\n| ☀️ 天气状况 | **晴天** |\n\n今天杭州天气晴朗，温度适宜，非常适合外出活动！😊]","type":"agent_result"}

data:{"content":"","type":"agent_end"}

```

历史消息：`GET /agent/scope/getMessages?userId=u1&sessionId=s1`

会话列表：`GET /agent/scope/getSessions?userId=u1`

API 文档：启动 agent-app 后访问 `http://localhost:8082/doc.html`（Knife4j）。

## 说明

- 智能体状态与上下文默认存储在 `~/.agentscope` 目录下（JsonFileAgentStateStore）。
- `AgentConfig` 中工具权限模式为 `PermissionMode.BYPASS`（不校验权限），仅供演示，生产环境请勿使用。
