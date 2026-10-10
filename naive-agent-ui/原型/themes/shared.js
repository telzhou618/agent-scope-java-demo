/* shared.js — 注入聊天页 mock 标记 + 明/暗切换 */
(function () {
  "use strict";

  /* 1.5px 线性 SVG 图标(统一描边风格) */
  var sw = 'viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"';
  var I = {
    plus: '<svg ' + sw + '><path d="M12 5v14M5 12h14"/></svg>',
    search: '<svg ' + sw + '><circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/></svg>',
    chevronDown: '<svg ' + sw + '><path d="m6 9 6 6 6-6"/></svg>',
    chevronRight: '<svg ' + sw + '><path d="m9 18 6-6-6-6"/></svg>',
    share: '<svg ' + sw + '><path d="M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8"/><path d="m16 6-4-4-4 4"/><path d="M12 2v13"/></svg>',
    more: '<svg ' + sw + '><circle cx="5" cy="12" r="1" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1" fill="currentColor" stroke="none"/><circle cx="19" cy="12" r="1" fill="currentColor" stroke="none"/></svg>',
    sun: '<svg ' + sw + '><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41"/></svg>',
    moon: '<svg ' + sw + '><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>',
    sparkFill: '<svg viewBox="0 0 24 24" fill="currentColor" stroke="none"><path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9L12 3z"/></svg>',
    gear: '<svg ' + sw + '><path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/><circle cx="12" cy="12" r="3"/></svg>',
    check: '<svg ' + sw + '><path d="M20 6 9 17l-5-5"/></svg>',
    thermo: '<svg ' + sw + '><path d="M14 14.76V3.5a2.5 2.5 0 0 0-5 0v11.26a4.5 4.5 0 1 0 5 0z"/></svg>',
    gauge: '<svg ' + sw + '><path d="m12 14 4-4"/><path d="M3.34 19a10 10 0 1 1 17.32 0"/></svg>',
    droplet: '<svg ' + sw + '><path d="M12 2.7 6.7 8.1a7.4 7.4 0 1 0 10.6 0L12 2.7z"/></svg>',
    wind: '<svg ' + sw + '><path d="M9.59 4.59A2 2 0 1 1 11 8H2m10.59 11.41A2 2 0 1 0 14 16H2m15.73-8.27A2.5 2.5 0 1 1 19.5 12H2"/></svg>',
    copy: '<svg ' + sw + '><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>',
    refresh: '<svg ' + sw + '><path d="M23 4v6h-6M1 20v-6h6"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg>',
    thumbUp: '<svg ' + sw + '><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3H14z"/><path d="M7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/></svg>',
    thumbDown: '<svg ' + sw + '><path d="M10 15v4a3 3 0 0 0 3 3l4-9V2H6.72a2 2 0 0 0-2 1.7l-1.38 9a2 2 0 0 0 2 2.3H10z"/><path d="M17 2h2.67A2.31 2.31 0 0 1 22 4v7a2.31 2.31 0 0 1-2.33 2H17"/></svg>',
    send: '<svg ' + sw + '><path d="m22 2-7 20-4-9-9-4 20-7z"/><path d="M22 2 11 13"/></svg>',
    user: '<svg ' + sw + '><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>'
  };

  function convItem(t, tag, time, active) {
    return '<div class="conv-item' + (active ? ' active' : '') + '">' +
      '<div class="conv-top"><span class="conv-title">' + t + '</span>' +
      '<span class="agent-tag tag-' + tag.toLowerCase() + '">' + tag + '</span></div>' +
      '<span class="conv-time">' + time + '</span></div>';
  }

  var markup =
    '<div class="app">' +
      '<aside class="sidebar">' +
        '<div class="brand"><span class="logo-dot"></span><span class="brand-name">Agent Platform</span></div>' +
        '<button class="new-chat">' + I.plus + '<span>新对话</span></button>' +
        '<div class="search-box">' + I.search + '<input placeholder="搜索对话" /></div>' +
        '<div class="conv-scroll">' +
          '<div class="conv-group"><div class="conv-group-title">置顶</div>' +
            convItem('杭州天气如何', 'Max', '刚刚', true) +
            convItem('季度营收分析周报', 'Plus', '昨天 18:02', false) +
          '</div>' +
          '<div class="conv-group"><div class="conv-group-title">今天</div>' +
            convItem('竞品调研摘要', 'Flash', '10:24', false) +
            convItem('帮我写一封请假邮件', 'Flash', '09:51', false) +
            convItem('SQL 慢查询优化建议', 'Plus', '09:12', false) +
            convItem('团队周报大纲润色', 'Max', '08:40', false) +
          '</div>' +
        '</div>' +
      '</aside>' +

      '<div class="main">' +
        '<header class="topbar">' +
          '<div class="topbar-left">' +
            '<button class="agent-picker"><span class="ap-icon">' + I.sparkFill + '</span><span>Agent Max</span><span class="ap-caret">' + I.chevronDown + '</span></button>' +
            '<span class="topbar-divider"></span>' +
            '<span class="topbar-title">杭州天气如何</span>' +
          '</div>' +
          '<div class="topbar-right">' +
            '<button class="icon-btn" title="分享">' + I.share + '</button>' +
            '<button class="icon-btn" title="更多">' + I.more + '</button>' +
            '<button class="icon-btn mode-toggle" id="mode-toggle" title="切换明暗">' +
              '<span class="i-moon">' + I.moon + '</span><span class="i-sun">' + I.sun + '</span>' +
            '</button>' +
            '<span class="avatar">' + I.user + '</span>' +
          '</div>' +
        '</header>' +

        '<main class="messages"><div class="msg-col">' +
          '<section class="msg-user">' +
            '<div class="bubble-user">杭州天气如何</div>' +
            '<span class="msg-meta">10:32 · 你</span>' +
          '</section>' +

          '<section class="msg-assistant">' +
            '<div class="assistant-head">' +
              '<span class="assistant-avatar">' + I.sparkFill + '</span>' +
              '<span class="assistant-name">Agent Max</span>' +
              '<span class="assistant-sub">· 已接入 12 个工具</span>' +
            '</div>' +

            '<button class="thinking-bar">' +
              '<span class="tb-spark">' + I.sparkFill + '</span><span>思考了 2 秒</span>' +
              '<span class="tb-caret">' + I.chevronRight + '</span>' +
            '</button>' +

            '<div class="tool-card"><div class="tool-head">' +
              '<span class="tool-gear">' + I.gear + '</span>' +
              '<span class="tool-name">queryWeather</span>' +
              '<code class="tool-args">{"city":"杭州"}</code>' +
              '<span class="tool-status">' + I.check + '<span>成功</span></span>' +
            '</div></div>' +

            '<div class="md">' +
              '<p class="md-lead">杭州今日天气晴好，适合出行，以下是实时气象数据：</p>' +
              '<ul class="weather-list">' +
                '<li><span class="wl-icon">' + I.thermo + '</span><span class="wl-label">温度</span><span class="wl-value">22°C</span></li>' +
                '<li><span class="wl-icon">' + I.gauge + '</span><span class="wl-label">体感</span><span class="wl-value">21°C</span></li>' +
                '<li><span class="wl-icon">' + I.droplet + '</span><span class="wl-label">湿度</span><span class="wl-value">69%</span></li>' +
                '<li><span class="wl-icon">' + I.wind + '</span><span class="wl-label">风速</span><span class="wl-value">13km/h</span></li>' +
              '</ul>' +
              '<p class="md-note">如需在你自己的服务里接入实时天气接口，可参考下面的 Python 示例：</p>' +
              '<div class="code-card">' +
                '<div class="code-head"><span class="code-lang">python</span>' +
                  '<button class="code-copy">' + I.copy + '<span>复制</span></button>' +
                '</div>' +
                '<pre><span class="tk-k">import</span> requests\n\n' +
'<span class="tk-k">def</span> <span class="tk-f">query_weather</span>(city: <span class="tk-f">str</span>) -&gt; <span class="tk-f">dict</span>:\n' +
'    <span class="tk-c">"""查询指定城市的实时天气"""</span>\n' +
'    resp = requests.<span class="tk-f">get</span>(<span class="tk-s">f"https://api.weather.dev/v1/now?city={city}"</span>)\n' +
'    resp.<span class="tk-f">raise_for_status</span>()\n' +
'    <span class="tk-k">return</span> resp.<span class="tk-f">json</span>()\n\n' +
'<span class="tk-k">if</span> __name__ == <span class="tk-s">"__main__"</span>:\n' +
'    <span class="tk-k">print</span>(<span class="tk-f">query_weather</span>(<span class="tk-s">"杭州"</span>))</pre>' +
              '</div>' +
            '</div>' +

            '<div class="msg-actions">' +
              '<button class="action-btn" title="复制">' + I.copy + '</button>' +
              '<button class="action-btn" title="重新生成">' + I.refresh + '</button>' +
              '<button class="action-btn" title="赞同">' + I.thumbUp + '</button>' +
              '<button class="action-btn" title="反对">' + I.thumbDown + '</button>' +
            '</div>' +
          '</section>' +
        '</div></main>' +

        '<footer class="composer-wrap">' +
          '<div class="composer">' +
            '<input placeholder="给 Agent 发消息,输入 / 唤起技能…" />' +
            '<button class="send-btn" title="发送">' + I.send + '</button>' +
          '</div>' +
          '<div class="composer-hint">Agent 可能会出错,请核验重要信息</div>' +
        '</footer>' +
      '</div>' +
    '</div>';

  document.body.insertAdjacentHTML('afterbegin', markup);

  /* 明/暗切换 */
  document.getElementById('mode-toggle').addEventListener('click', function () {
    var el = document.documentElement;
    el.setAttribute('data-mode', el.getAttribute('data-mode') === 'dark' ? 'light' : 'dark');
  });

  /* 会话选中态切换 */
  document.querySelector('.conv-scroll').addEventListener('click', function (e) {
    var item = e.target.closest('.conv-item');
    if (!item) return;
    document.querySelectorAll('.conv-item.active').forEach(function (n) { n.classList.remove('active'); });
    item.classList.add('active');
  });

  /* 代码复制小交互 */
  document.querySelector('.code-copy').addEventListener('click', function () {
    var btn = this;
    var html = btn.innerHTML;
    btn.innerHTML = I.check + '<span>已复制</span>';
    setTimeout(function () { btn.innerHTML = html; }, 1200);
  });
})();
