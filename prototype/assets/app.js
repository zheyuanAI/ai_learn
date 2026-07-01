const groups = [
  {
    title: "项目导航",
    items: [
      { href: "../index.html", label: "原型总览", pageId: "prototype-index", detail: "导航图与六条演示主线" }
    ]
  },
  {
    title: "基础页面",
    items: [
      { href: "login.html", label: "登录页", pageId: "login", detail: "租户、用户、角色上下文" },
      { href: "dashboard.html", label: "首页看板", pageId: "dashboard", detail: "库存、生产、设备、告警总览" }
    ]
  },
  {
    title: "业务域页面",
    items: [
      { href: "master-data.html", label: "主数据", pageId: "master-data", detail: "商品、仓库、库位" },
      { href: "purchase-inbound.html", label: "采购入库", pageId: "purchase-inbound", detail: "采购、入库、上架" },
      { href: "sales-outbound.html", label: "销售出库", pageId: "sales-outbound", detail: "销售、冻结、拣货、出库" },
      { href: "work-order.html", label: "工单执行", pageId: "work-order", detail: "工单、派工、报工、质检" },
      { href: "device-alarm.html", label: "设备与告警", pageId: "device-alarm", detail: "设备详情、遥测、告警" },
      { href: "site-map.html", label: "厂区地图", pageId: "site-map", detail: "点位、区域、联动告警" },
      { href: "digital-twin.html", label: "三维展示", pageId: "digital-twin", detail: "简化 3D 和状态联动" },
      { href: "ai-assistant.html", label: "AI 聊天", pageId: "ai-assistant", detail: "问答、工具调用、来源" },
      { href: "knowledge-base.html", label: "知识库管理", pageId: "knowledge-base", detail: "文档上传、切分、索引" },
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
      <p>原型优先，先冻结字段、状态、接口和权限，再推进真实功能与自动化测试。</p>
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
