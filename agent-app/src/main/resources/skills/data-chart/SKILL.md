---
name: data-chart
description: 当用户要求绘制图表、数据可视化（折线图、柱状图、饼图、散点图、雷达图、漏斗图、仪表盘、热力图、K线图、箱线图、甘特图等）时使用。
---

需要绘制图表时，在回复正文中输出一个围栏代码块，语言标记为 echarts，内容是一个合法的 JSON 对象（ECharts option）。

界面只注册了以下 10 种图表类型：折线 line、柱状 bar、饼图 pie、散点 scatter、雷达 radar、漏斗 funnel、仪表盘 gauge、热力图 heatmap、K线 candlestick、箱线图 boxplot。不要使用其他类型（桑基图、旭日图、地图等无法渲染）。

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

散点图示例（多系列时 legend 与 series.name 对应）：

```echarts
{"tooltip":{"trigger":"item"},"legend":{},"xAxis":{"type":"value","name":"身高(cm)"},"yAxis":{"type":"value","name":"体重(kg)"},"series":[{"name":"男","type":"scatter","data":[[172,68],[178,75],[165,60],[180,82]]},{"name":"女","type":"scatter","data":[[160,52],[168,58],[155,47],[170,63]]}]}
```

雷达图示例（indicator 声明各维度及最大值）：

```echarts
{"tooltip":{},"legend":{},"radar":{"indicator":[{"name":"沟通","max":100},{"name":"技术","max":100},{"name":"管理","max":100},{"name":"协作","max":100},{"name":"创新","max":100}]},"series":[{"type":"radar","data":[{"name":"甲","value":[80,90,70,85,75]},{"name":"乙","value":[70,65,88,72,90]}]}]}
```

漏斗图示例（数据按从大到小排列）：

```echarts
{"tooltip":{"trigger":"item"},"legend":{},"series":[{"type":"funnel","data":[{"name":"访问","value":100},{"name":"咨询","value":80},{"name":"订单","value":60},{"name":"支付","value":40},{"name":"复购","value":20}]}]}
```

仪表盘示例（单值指标，detail.formatter 控制数值格式）：

```echarts
{"tooltip":{},"series":[{"type":"gauge","progress":{"show":true},"detail":{"formatter":"{value}%"},"data":[{"name":"完成率","value":66}]}]}
```

热力图示例（必须配 visualMap，数据为 [x轴下标, y轴下标, 值] 三元组）：

```echarts
{"tooltip":{},"xAxis":{"type":"category","data":["周一","周二","周三","周四","周五"]},"yAxis":{"type":"category","data":["上午","下午","晚上"]},"visualMap":{"min":0,"max":20,"calculable":true},"series":[{"type":"heatmap","data":[[0,0,5],[1,0,12],[2,0,8],[3,0,15],[4,0,9],[0,1,10],[1,1,18],[2,1,6],[3,1,20],[4,1,11],[0,2,3],[1,2,7],[2,2,13],[3,2,17],[4,2,4]]}]}
```

K线图示例（数据为 [开盘价, 收盘价, 最低价, 最高价] 四元组，涨跌配色由界面主题按红涨绿跌处理）：

```echarts
{"tooltip":{"trigger":"axis"},"xAxis":{"type":"category","data":["周一","周二","周三","周四","周五"]},"yAxis":{"type":"value"},"series":[{"type":"candlestick","data":[[100,108,98,110],[108,104,101,112],[104,112,103,115],[112,109,106,116],[109,118,108,120]]}]}
```

箱线图示例（数据为 [最小值, Q1, 中位数, Q3, 最大值] 五元组，展示数据分布与离群点）：

```echarts
{"tooltip":{"trigger":"item"},"xAxis":{"type":"category","data":["一班","二班","三班"]},"yAxis":{"type":"value","name":"分数"},"series":[{"type":"boxplot","data":[[55,68,78,86,98],[48,62,74,84,95],[60,70,80,88,100]]}]}
```

甘特图示例（没有原生甘特图类型，用"透明占位系列 + 相同 stack 的横向柱状图"实现：占位系列记录开始时间并设为透明，持续时间系列叠在上面；yAxis 用 inverse:true 让第一个任务在最上面；占位系列是唯一允许设置颜色的场景）：

```echarts
{"title":{"text":"软件项目开发甘特图"},"tooltip":{"trigger":"item"},"grid":{"left":10,"right":30,"top":40,"bottom":10,"containLabel":true},"xAxis":{"type":"value","name":"天数","axisLabel":{"formatter":"第{value}天"}},"yAxis":{"type":"category","inverse":true,"data":["需求分析","UI/UX设计","架构设计","数据库设计","前端开发","后端开发","接口联调","系统测试","Bug修复","上线部署"]},"series":[{"name":"偏移","type":"bar","stack":"gantt","itemStyle":{"color":"transparent"},"emphasis":{"itemStyle":{"color":"transparent"}},"tooltip":{"show":false},"data":[0,7,17,22,30,30,54,62,72,80]},{"name":"持续时间","type":"bar","stack":"gantt","barWidth":"55%","label":{"show":true,"position":"inside","formatter":"{c}天"},"data":[7,10,5,8,21,24,8,10,8,5]}]}
```

规则：
1. 只输出合法 JSON：不加注释、不用函数、不用单引号、字符串一律双引号，不要输出 JSON 以外的任何包装。
2. option 必须包含 tooltip；多系列时必须加 legend（甘特图除外：占位系列是辅助数据，加 legend 会污染图例并与轴标签重叠）；酌情加 title。
3. 不要设置 color、backgroundColor 和固定宽高——配色（含 K 线涨跌色、热力图渐变、仪表盘分段色）和尺寸由界面主题统一处理；唯一例外是甘特图占位系列的 itemStyle 必须设为 transparent。
4. 大数据量可加 dataZoom；堆叠柱状图给各 series 设相同的 "stack" 值；横向柱状图交换 xAxis/yAxis 的 type。
5. 图表数据必须有依据：来自对话上下文或工具查询结果，禁止编造；缺数据时先调用工具或向用户索取。
6. 一个回复可包含多个图表块，每个图表前后用一句话说明图表内容。
