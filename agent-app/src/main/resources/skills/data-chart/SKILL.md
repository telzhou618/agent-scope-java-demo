---
name: data-chart
description: 当用户要求绘制图表、数据可视化（折线图、柱状图、饼图、散点图、雷达图、漏斗图、仪表盘、热力图、K线图等）时使用。
---

需要绘制图表时，在回复正文中输出一个围栏代码块，语言标记为 echarts，内容是一个合法的 JSON 对象（ECharts option）。

折线图示例：

```echarts
{"tooltip":{"trigger":"axis"},"legend":{},"xAxis":{"type":"category","data":["周一","周二","周三","周四","周五"]},"yAxis":{"type":"value","name":"温度(°C)"},"series":[{"name":"最高气温","type":"line","smooth":true,"data":[22,25,23,27,26]},{"name":"最低气温","type":"line","smooth":true,"data":[15,16,14,18,17]}]}
```

柱状图示例：

```echarts
{"tooltip":{"trigger":"axis"},"xAxis":{"type":"category","data":["键盘","鼠标","耳机"]},"yAxis":{"type":"value"},"series":[{"type":"bar","data":[128,256,96],"label":{"show":true,"position":"top"}}]}
```

饼图示例：

```echarts
{"tooltip":{"trigger":"item"},"legend":{},"series":[{"type":"pie","radius":["40%","70%"],"label":{"show":true,"formatter":"{b}: {d}%"},"data":[{"name":"手机","value":45},{"name":"电脑","value":30},{"name":"平板","value":25}]}]}
```

规则：
1. 只输出合法 JSON：不加注释、不用函数、不用单引号、字符串一律双引号，不要输出 JSON 以外的任何包装。
2. option 必须包含 tooltip；多系列时必须加 legend；酌情加 title。
3. 不要设置 color、backgroundColor 和固定宽高——配色和尺寸由界面主题统一处理。
4. 常用图表类型：折线 line、柱状 bar、饼图 pie、散点 scatter、雷达 radar、漏斗 funnel、仪表盘 gauge、热力图 heatmap（需配 visualMap）、K线 candlestick、箱线图 boxplot；大数据量可加 dataZoom。
5. 图表数据必须有依据：来自对话上下文或工具查询结果，禁止编造；缺数据时先调用工具或向用户索取。
6. 一个回复可包含多个图表块，每个图表前后用一句话说明图表内容。
