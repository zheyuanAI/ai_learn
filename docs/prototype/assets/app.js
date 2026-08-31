const groups = [
  {
    title: "项目导航",
    items: [
      { href: "index.html", label: "原型总览", pageId: "prototype-index", detail: "一期黄金业务闭环" },
      { href: "workflow-wireframe.html", label: "流程线框工作台", pageId: "workflow-wireframe", detail: "角色、状态、接口联动评审" }
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
      { href: "purchase-inbound.html", label: "采购收货", pageId: "purchase-inbound", detail: "外观验收、质量隔离、到货质检与上架" },
      { href: "sales-outbound.html", label: "销售交付", pageId: "sales-outbound", detail: "直接拣货自动预留、移位与发货扣减" },
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

/**
 * 根据当前页面所在目录计算导航链接。
 * 入参为导航项和当前页面标识，出参为可直接用于 href 的相对路径。
 * 核心流程区分根目录总览页与 pages 子目录，避免总览页跳转业务页面时路径错误。
 */
function resolveNavigationHref(item, currentPageId) {
  const isPrototypeIndex = currentPageId === "prototype-index";
  if (item.pageId === "prototype-index") {
    return isPrototypeIndex ? "index.html" : "../index.html";
  }
  return isPrototypeIndex ? `pages/${item.href}` : item.href;
}

/**
 * 渲染静态原型左侧导航。
 * 入参为当前页面标识，无返回值；核心流程生成品牌区、分组导航和当前页面高亮。
 * 修改原因：低保真工作台需要进入统一导航，同时修复根目录与 pages 子目录的相对路径差异。
 */
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
      const resolvedHref = resolveNavigationHref(item, currentPageId);
      return `
        <a class="nav-link ${active}" href="${resolvedHref}">
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

  rail.innerHTML = `
    <button class="rail-toggle" type="button" aria-expanded="false" aria-controls="prototype-navigation">
      <span>原型页面导航</span>
      <span class="rail-toggle-mark" aria-hidden="true">＋</span>
    </button>
    <div class="rail-content" id="prototype-navigation">
      ${brand}${sections}
    </div>
  `;

  const toggle = rail.querySelector(".rail-toggle");
  toggle?.addEventListener("click", () => {
    const isOpen = rail.classList.toggle("is-open");
    toggle.setAttribute("aria-expanded", String(isOpen));
    const mark = toggle.querySelector(".rail-toggle-mark");
    if (mark) mark.textContent = isOpen ? "－" : "＋";
  });
}

/**
 * 在任意操作原型页面右上角加入业务架构主入口。
 * 入参为当前页面标识，无返回值。
 * 核心流程只区分原型根入口与 pages 子目录的相对路径，生成普通直达链接，不维护模块页面映射。
 */
function renderBusinessArchitectureJump(currentPageId) {
  const workspace = document.querySelector(".workspace");
  if (!workspace || workspace.querySelector(".prototype-space-jump")) return;
  const href = currentPageId === "prototype-index"
    ? "../业务架构/index.html"
    : "../../业务架构/index.html";
  const link = document.createElement("a");
  link.className = "prototype-space-jump";
  link.href = href;
  link.innerHTML = '业务架构 <span aria-hidden="true">↗</span>';
  workspace.insertAdjacentElement("afterbegin", link);
}

document.addEventListener("DOMContentLoaded", () => {
  const currentPageId = document.body.dataset.page || "";
  renderRail(currentPageId);
  renderBusinessArchitectureJump(currentPageId);
});
