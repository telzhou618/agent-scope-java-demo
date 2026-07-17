package com.example.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;


@Component
public class WeatherTools {
    @McpTool(name = "queryWeather", description = "根据城市名称查询模拟天气信息，返回温度、湿度和天气状况")
    public String queryWeather(
            @McpToolParam(description = "城市名称，如: 北京、上海、杭州", required = true) String city) {
        return "城市" + city + "天气信息，温度为25度，湿度为60%，天气状况为晴天。";
    }
}
