package com.example.mcp.tools;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 天气查询：调用 wttr.in 免费接口（无需 API Key），返回真实天气信息
 */
@Component
public class WeatherTools {

    private static final String API_URL = "https://wttr.in/";

    /** wttr.in weatherCode → 中文天气状况（接口的中文翻译不稳定，自行映射） */
    private static final Map<String, String> WEATHER_CODE_ZH = Map.<String, String>ofEntries(
            Map.entry("113", "晴"),
            Map.entry("116", "多云"),
            Map.entry("119", "多云"),
            Map.entry("122", "阴"),
            Map.entry("143", "雾"),
            Map.entry("176", "阵雨"),
            Map.entry("179", "阵雪"),
            Map.entry("182", "雨夹雪"),
            Map.entry("185", "毛毛雨"),
            Map.entry("200", "雷阵雨"),
            Map.entry("227", "风吹雪"),
            Map.entry("230", "暴雪"),
            Map.entry("248", "雾"),
            Map.entry("260", "冻雾"),
            Map.entry("263", "小毛毛雨"),
            Map.entry("266", "毛毛雨"),
            Map.entry("281", "冻毛毛雨"),
            Map.entry("284", "冻毛毛雨"),
            Map.entry("293", "小雨"),
            Map.entry("296", "小雨"),
            Map.entry("299", "中雨"),
            Map.entry("302", "中雨"),
            Map.entry("305", "大雨"),
            Map.entry("308", "大雨"),
            Map.entry("311", "冻雨"),
            Map.entry("314", "冻雨"),
            Map.entry("317", "雨夹雪"),
            Map.entry("320", "雨夹雪"),
            Map.entry("323", "小雪"),
            Map.entry("326", "中雪"),
            Map.entry("329", "大雪"),
            Map.entry("332", "大雪"),
            Map.entry("335", "暴雪"),
            Map.entry("338", "暴雪"),
            Map.entry("350", "冰雹"),
            Map.entry("353", "小阵雨"),
            Map.entry("356", "阵雨"),
            Map.entry("359", "强阵雨"),
            Map.entry("362", "小阵雪"),
            Map.entry("365", "阵雪"),
            Map.entry("368", "阵雪"),
            Map.entry("371", "强阵雪"),
            Map.entry("374", "小冰雹"),
            Map.entry("377", "冰雹"),
            Map.entry("386", "雷阵雨"),
            Map.entry("389", "雷阵雨"),
            Map.entry("392", "雷阵雨伴雪"),
            Map.entry("395", "雷阵雨伴雪")
    );

    private final RestClient restClient;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public WeatherTools() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @McpTool(name = "query_weather", description = "根据城市名称查询真实天气信息，返回温度、体感温度、湿度和天气状况")
    public String queryWeather(
            @McpToolParam(description = "城市名称，如: 北京、上海、杭州", required = true) String city) {
        try {
            // wttr.in 要求城市名百分号编码（原始 UTF-8 字节会 500）：
            // 手动编码后以 URI 对象传入，避免 RestClient 对 % 二次编码；
            // 响应 body 是 JSON 但 content-type 是 text/plain，按字符串读取再解析
            String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
            String body = restClient.get()
                    .uri(URI.create(API_URL + encodedCity + "?format=j1"))
                    .retrieve()
                    .body(String.class);
            JsonNode current = jsonMapper.readTree(body)
                    .path("current_condition")
                    .path(0);
            if (current.isMissingNode() || !current.has("temp_C")) {
                return "未查询到城市「" + city + "」的天气信息，请检查城市名称是否正确。";
            }
            String code = current.path("weatherCode").asText();
            String desc = WEATHER_CODE_ZH.getOrDefault(code,
                    current.path("weatherDesc").path(0).path("value").asText("未知"));
            return "城市" + city + "天气信息：温度" + current.path("temp_C").asText() + "°C"
                    + "，体感温度" + current.path("FeelsLikeC").asText() + "°C"
                    + "，湿度" + current.path("humidity").asText() + "%"
                    + "，天气状况为" + desc
                    + "，风速" + current.path("windspeedKmph").asText() + "km/h。";
        } catch (Exception e) {
            // wttr.in 对未知城市返回 500 + "location not found"，转成友好提示
            String msg = String.valueOf(e.getMessage());
            if (msg.contains("location not found")) {
                return "未查询到城市「" + city + "」的天气信息，请检查城市名称是否正确。";
            }
            return "天气查询失败：" + msg + "，请稍后重试。";
        }
    }
}
