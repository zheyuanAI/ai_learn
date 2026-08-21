const groups = [
  {
    title: "项目导航",
    items: [
      { href: "../index.html", label: "原型总览", pageId: "prototype-index", detail: "一期黄金业务闭环" }
    ]
  },
  {
    title: "基础页面",
    items: [
      { href: "login.html", label: "登录页", pageId: "login", detail: "租户、用户、角色上下文" },
      { href: "dashboard.html", label: "首页看板", pageId: "dashboard", detail: "黄金闭环事实只读总览" }
    ]
  },
  {
    title: "业务域页面",
    items: [
      { href: "master-data.html", label: "主数据", pageId: "master-data", detail: "商品、仓库、库位" },
      { href: "purchase-inbound.html", label: "采购收货", pageId: "purchase-inbound", detail: "人工来源、收货暂存、上架移位" },
      { href: "sales-outbound.html", label: "销售交付", pageId: "sales-outbound", detail: "库存预留、拣货移位、发货扣减" },
      { href: "work-order.html", label: "制造执行", pageId: "work-order", detail: "领退料、工序执行、报工质检" },
      { href: "device-alarm.html", label: "设备事实", pageId: "device-alarm", detail: "消息去重、遥测、状态、告警" },
      { href: "site-map.html", label: "厂区地图", pageId: "site-map", detail: "业务事实只读空间视图" },
      { href: "digital-twin.html", label: "三维展示（二期设计资产）", pageId: "digital-twin", detail: "不进入一期开发与验收" },
      { href: "ai-assistant.html", label: "AI 只读助手", pageId: "ai-assistant", detail: "受控查询、来源与审计" },
      { href: "knowledge-base.html", label: "知识库（二期设计资产）", pageId: "knowledge-base", detail: "不进入一期开发与验收" },
      { href: "tool-audit.html", label: "调用审计", pageId: "tool-audit", detail: "输入摘要、耗时、状态" }
    ]
  }
];

function renderRail(currentPageId) {
  const rail = document.querySelector(".rail");
  if (!rail) return;

  const brand = `
    <div class="brand">
      <p class="brand-kicker">AI Learn</p>
      <h1>工业协同原型台</h1>
      <p>一期目标设计围绕一条黄金业务闭环；原型不代表功能已经实现。</p>
    </div>
  `;

  const sections = groups.map((group) => {
    const items = group.items.map((item) => {
      const active = item.pageId === currentPageId ? "is-active" : "";
      return `
        <a class="nav-link ${active}" href="${item.href}">
          ${item.label}
          <span>${item.detail}</span>
        </a>
      `;
    }).join("");

    return `
      <section class="nav-group">
        <p class="nav-group-title">${group.title}</p>
        <div class="nav-list">${items}</div>
      </section>
    `;
  }).join("");

  rail.innerHTML = `${brand}${sections}`;
}

document.addEventListener("DOMContentLoaded", () => {
  renderRail(document.body.dataset.page || "");
});
