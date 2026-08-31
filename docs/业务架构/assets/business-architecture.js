const bizData = window.businessArchitectureData;
const bizPage = document.body.dataset.bizPage;
const bizModuleId = document.body.dataset.bizModule;
const bizViewLabels = {
  topology: "模块拓扑",
  goldenFlow: "黄金闭环",
  factFlow: "事实流",
  boundaries: "职责边界",
  roadmap: "一期与扩展"
};
let activeBizOverviewView = "topology";

/**
 * 转义业务架构页面中的动态文本。
 * 入参为任意可显示值，出参为安全 HTML 字符串。
 * 核心流程替换 HTML 特殊字符，避免架构数据破坏页面结构。
 */
function escapeBizHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (character) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#039;"
  }[character]));
}

/**
 * 根据模块编码获取业务架构模块数据。
 * 入参为模块编码，出参为模块对象或 undefined。
 * 核心流程只查询全局事实数据，不创建页面级副本。
 */
function getBizModule(moduleId) {
  return bizData.modules.find((module) => module.id === moduleId);
}

/**
 * 渲染模块可信度标签。
 * 入参为模块对象，出参为标签 HTML。
 * 核心流程依据模块数据展示规则确认状态，当前一期七模块均为 confirmed（已确认）。
 */
function renderBizConfidence(module) {
  return `<span class="biz-confidence ${escapeBizHtml(module.confidence)}"><i aria-hidden="true"></i>${escapeBizHtml(module.confidenceLabel)}</span>`;
}

/**
 * 渲染业务架构空间的公共页头。
 * 入参为当前页面标题，出参为页头 HTML。
 * 核心流程提供预览、总览、模块菜单和原型主入口，不建立模块级原型映射。
 */
function renderBizHeader(currentTitle) {
  const moduleLinks = bizData.modules.map((module) => `
    <a href="${escapeBizHtml(module.page)}">${escapeBizHtml(module.title)}<small>${escapeBizHtml(module.confidenceLabel)}</small></a>
  `).join("");
  return `
    <header class="biz-topbar">
      <a class="biz-brand" href="index.html"><span class="biz-brand-mark">A</span><span><strong>业务架构</strong><small>AI LEARN WMS</small></span></a>
      <nav class="biz-primary-nav" aria-label="业务架构内部导航">
        <a href="index.html" ${bizPage === "preview" ? 'aria-current="page"' : ""}>预览</a>
        <a href="总览.html" ${bizPage === "overview" ? 'aria-current="page"' : ""}>总览</a>
        <details class="biz-module-menu">
          <summary>${bizPage === "module" ? escapeBizHtml(currentTitle) : "业务模块"} <span>⌄</span></summary>
          <div>${moduleLinks}</div>
        </details>
      </nav>
      <a class="biz-space-jump" href="../prototype/index.html">操作原型 <span aria-hidden="true">↗</span></a>
    </header>
  `;
}

/**
 * 渲染页面底部的事实来源与实现状态说明。
 * 无入参，出参为页脚 HTML。
 * 核心流程统一声明目标设计性质，避免架构图被误解为已实现能力。
 */
function renderBizFooter() {
  return `<footer class="biz-footer"><span>ARCHITECTURE / TARGET DESIGN</span><p>${escapeBizHtml(bizData.meta.disclaimer)}</p><a href="../specs/00-project/项目概述.md">查看项目事实源 ↗</a></footer>`;
}

/**
 * 渲染业务架构预览主入口。
 * 无入参和返回值。
 * 核心流程生成项目摘要、总览入口、可信度图例、模块卡和一期范围分层。
 */
function renderBizPreview() {
  const app = document.querySelector("#bizApp");
  if (!app) return;
  const cards = bizData.modules.map((module, index) => `
    <a class="biz-module-card" href="${escapeBizHtml(module.page)}" style="--delay:${index * 45}ms">
      <span class="biz-module-code">${escapeBizHtml(module.code)}</span>${renderBizConfidence(module)}
      <h2>${escapeBizHtml(module.title)}</h2><p>${escapeBizHtml(module.tagline)}</p>
      <div><span>${module.flow.length} 个关键节点</span><strong>进入模块 <i aria-hidden="true">→</i></strong></div>
    </a>
  `).join("");
  app.innerHTML = `
    ${renderBizHeader("业务架构预览")}
    <main class="biz-main">
      <section class="biz-preview-hero">
        <div><span class="biz-kicker">BUSINESS ARCHITECTURE / PREVIEW</span><h1>业务架构<br><em>预览</em></h1><p>${escapeBizHtml(bizData.meta.statement)}</p></div>
        <a class="biz-overview-entry" href="总览.html"><span>总体业务架构</span><strong>查看模块关系与黄金闭环</strong><i aria-hidden="true">↗</i><small>模块拓扑 · 事实流 · 职责边界 · 实施路线</small></a>
      </section>
      <section class="biz-status-strip" aria-label="架构可信度图例">
        <div><span>当前范围</span><strong>一期黄金闭环</strong></div>
        <div><span>业务规则</span><strong class="is-confirmed">七模块已确认</strong></div>
        <div><span>当前阶段</span><strong>等待整体复核</strong></div>
        <div><span>代码状态</span><strong class="is-warning">业务接口未实现</strong></div>
      </section>
      <section class="biz-section">
        <header class="biz-section-head"><div><span>01 / MODULE INDEX</span><h2>业务模块</h2></div><p>先看全局关系，再进入单个模块核对职责、状态和异常。</p></header>
        <div class="biz-module-grid">${cards}</div>
      </section>
      <section class="biz-review-update" aria-label="架构评审动态">
        <article><span>BUSINESS RULES READY</span><h2>规则确认完成</h2><p>${escapeBizHtml(bizData.meta.reviewUpdate.completed)}</p><a href="总览.html#goldenFlow">查看黄金闭环 →</a></article>
        <article><span>YOUR REVIEW</span><h2>等待整体复核</h2><p>${escapeBizHtml(bizData.meta.reviewUpdate.next)}</p><a href="总览.html">开始整体复核 →</a></article>
      </section>
      <section class="biz-scope-grid">
        <article><span>一期优先</span><h2>可信事实闭环</h2><p>采购、库存、销售、制造和设备围绕同一条可追溯业务链协作。</p><ul><li>显式状态迁移</li><li>事务、幂等与并发安全</li><li>库存事实唯一</li><li>IoT 事实独立</li></ul></article>
        <article class="future"><span>未来候选</span><h2>计划与商业深化</h2><p>一期事实内核稳定后，再引入更复杂的计划和商业能力。</p><ul><li>MRP / APS</li><li>复杂批次与序列号</li><li>退货与财务</li><li>可靠事件与三维场景</li></ul></article>
      </section>
    </main>${renderBizFooter()}
  `;
}

/**
 * 渲染单个物理服务节点。
 * 入参为服务架构对象，出参为可交互按钮 HTML。
 * 核心流程统一输出端口、服务名称和职责摘要，并保留稳定详情标识。
 */
function renderBizTopologyNode(service) {
  return `
    <button type="button" class="biz-topology-node ${service.id === "core" ? "core" : ""}" data-biz-detail-kind="service" data-biz-detail-id="${escapeBizHtml(service.id)}">
      <span>${escapeBizHtml(service.port)}</span><strong>${escapeBizHtml(service.title)}</strong><small>${escapeBizHtml(service.detail)}</small>
    </button>
  `;
}

/**
 * 根据总览视图编码渲染可交互架构图。
 * 入参为视图编码，出参为对应图形 HTML。
 * 核心流程分别处理物理拓扑、黄金闭环、事实分层、职责边界和实施路线。
 */
function renderBizOverviewCanvas(view) {
  if (view === "topology") {
    const serviceById = (id) => bizData.topology.services.find((service) => service.id === id);
    const branchIds = ["auth", "core", "iot-service"];
    return `<div class="biz-topology">
      <div class="biz-topology-flow">
        <div class="biz-topology-entry">${renderBizTopologyNode(serviceById("frontend"))}<span class="biz-flow-arrow" aria-hidden="true">→</span>${renderBizTopologyNode(serviceById("gateway"))}</div>
        <div class="biz-topology-router" aria-hidden="true"><span>ROUTE</span><i>→</i></div>
        <div class="biz-topology-branches">${branchIds.map((id) => `<div><span aria-hidden="true">↳</span>${renderBizTopologyNode(serviceById(id))}</div>`).join("")}</div>
      </div>
      <div class="biz-topology-note"><span>路由边界</span><p>Gateway 根据请求目标分别路由到 Auth、Core 或 IoT；IoT 管控请求不先经过 Core。</p></div>
      <div class="biz-core-cutaway"><span>CORE / LOGICAL MODULES</span><div>${bizData.topology.coreModules.map((item) => `<span>${escapeBizHtml(item)}</span>`).join("")}</div></div>
    </div>`;
  }
  if (view === "goldenFlow") {
    return `<div class="biz-golden-flow">${bizData.goldenFlow.map((step) => `
      <button type="button" data-biz-detail-kind="golden" data-biz-detail-id="${escapeBizHtml(step.id)}"><span>${escapeBizHtml(step.index)}</span><strong>${escapeBizHtml(step.title)}</strong><small>${escapeBizHtml(step.fact)}</small></button>
    `).join('<i aria-hidden="true">→</i>')}</div>`;
  }
  if (view === "factFlow") {
    return `<div class="biz-fact-flow">${bizData.factFlow.map((layer, index) => `
      <button type="button" data-biz-detail-kind="fact" data-biz-detail-id="${index}"><span>0${index + 1}</span><h3>${escapeBizHtml(layer.layer)}</h3><div>${layer.items.map((item) => `<em>${escapeBizHtml(item)}</em>`).join("")}</div><p>${escapeBizHtml(layer.note)}</p></button>
    `).join('<i aria-hidden="true">↓</i>')}</div>`;
  }
  if (view === "boundaries") {
    return `<div class="biz-boundary-grid">${bizData.boundaries.map((item, index) => `
      <button type="button" data-biz-detail-kind="boundary" data-biz-detail-id="${index}"><span>${String(index + 1).padStart(2, "0")}</span><strong>${escapeBizHtml(item.title)}</strong><small>${escapeBizHtml(item.owner)}</small><p>${escapeBizHtml(item.rule)}</p></button>
    `).join("")}</div>`;
  }
  return `<div class="biz-roadmap">${bizData.roadmap.map((item, index) => `
    <button type="button" data-biz-detail-kind="roadmap" data-biz-detail-id="${index}"><span>${escapeBizHtml(item.phase)}</span><strong>${escapeBizHtml(item.title)}</strong><p>${escapeBizHtml(item.detail)}</p></button>
  `).join("")}</div>`;
}

/**
 * 解析总览节点对应的详情。
 * 入参为节点类型和节点标识，出参为标题、分类、说明、结构化详情和可选模块链接。
 * 核心流程优先读取黄金节点自己的角色、输入、输出、状态、异常和事实来源，仅在节点未配置时回退模块字段，避免复合角色误导。
 */
function resolveBizOverviewDetail(kind, id) {
  if (kind === "service") {
    const item = bizData.topology.services.find((service) => service.id === id);
    return {
      label: "物理服务", title: item.title, text: item.detail,
      sections: [
        { label: "角色", items: item.roles }, { label: "输入", items: item.inputs }, { label: "输出", items: item.outputs },
        { label: "状态", items: item.states }, { label: "异常 / 边界", items: item.exceptions }, { label: "事实来源", items: item.sources }
      ]
    };
  }
  if (kind === "golden") {
    const item = bizData.goldenFlow.find((step) => step.id === id);
    if (!item) return null;
    const module = getBizModule(item.module);
    return {
      label: `黄金闭环 / ${item.index}`, title: item.title, text: item.detail || `${item.fact}由${module?.title || item.module}相关模块形成或解释。`, href: module?.page,
      sections: [
        { label: "角色", items: item.roles || module?.roles }, { label: "输入", items: item.inputs || module?.inputs }, { label: "输出", items: item.outputs || module?.outputs },
        { label: "状态", items: item.states || module?.states }, { label: "异常", items: item.exceptions || module?.exceptions?.slice(0, 3) }, { label: "事实来源", items: item.sources || module?.specs?.map((spec) => spec.label) }
      ]
    };
  }
  if (kind === "fact") {
    const item = bizData.factFlow[Number(id)];
    return {
      label: "事实分层", title: item.layer, text: item.note,
      sections: [
        { label: "包含对象", items: item.items }, { label: "输入", items: [Number(id) === 0 ? "用户与业务需求" : bizData.factFlow[Number(id) - 1].layer] },
        { label: "输出", items: [Number(id) === bizData.factFlow.length - 1 ? "授权用户的理解与决策" : bizData.factFlow[Number(id) + 1].layer] },
        { label: "边界", items: ["只由所属领域维护，不建立第二套事实"] }, { label: "事实来源", items: ["项目架构设计", "各领域模型"] }
      ]
    };
  }
  if (kind === "boundary") {
    const item = bizData.boundaries[Number(id)];
    return {
      label: `职责边界 / ${item.owner}`, title: item.title, text: item.rule,
      sections: [
        { label: "责任方", items: [item.owner] }, { label: "输入", items: ["跨模块命令或查询"] }, { label: "输出", items: ["受边界保护的领域事实"] },
        { label: "状态", items: ["由事实所属模块维护"] }, { label: "异常", items: ["越权写入、跨租户引用或绕过应用服务必须拒绝"] }, { label: "事实来源", items: ["项目架构设计 / 横切约束"] }
      ]
    };
  }
  const item = bizData.roadmap[Number(id)];
  return {
    label: `实施路线 / ${item.phase}`, title: item.title, text: item.detail,
    sections: [
      { label: "阶段", items: [item.phase] }, { label: "输入", items: [Number(id) === 0 ? "架构与领域规格" : bizData.roadmap[Number(id) - 1].title] },
      { label: "输出", items: [item.title] }, { label: "状态", items: [item.phase === "未来" ? "扩展候选" : "一期目标设计"] },
      { label: "异常 / 风险", items: ["未冻结规则不得表述为已实现能力"] }, { label: "事实来源", items: ["正式项目计划", "各模块验收标准"] }
    ]
  };
}

/**
 * 渲染节点的结构化详情清单。
 * 入参为详情分组数组，出参为角色、输入、输出、状态、异常和事实来源的 HTML。
 * 核心流程过滤空分组并逐项转义，使不同总览视图复用同一详情结构。
 */
function renderBizOverviewDetailSections(sections = []) {
  return `<div class="biz-node-detail-grid">${sections.filter((section) => section.items?.length).map((section) => `
    <section><span>${escapeBizHtml(section.label)}</span><ul>${section.items.map((item) => `<li>${escapeBizHtml(item)}</li>`).join("")}</ul></section>
  `).join("")}</div>`;
}

/**
 * 更新总览右侧节点详情。
 * 入参为节点类型和节点标识，无返回值。
 * 核心流程更新 aria-live 区域，并为可进入模块的节点提供链接。
 */
function updateBizOverviewDetail(kind, id) {
  const target = document.querySelector("#bizOverviewDetail");
  if (!target) return;
  const detail = resolveBizOverviewDetail(kind, id);
  if (!detail) return;
  target.innerHTML = `<span>${escapeBizHtml(detail.label)}</span><h3>${escapeBizHtml(detail.title)}</h3><p>${escapeBizHtml(detail.text)}</p>${renderBizOverviewDetailSections(detail.sections)}${detail.href ? `<a href="${escapeBizHtml(detail.href)}">进入模块架构 →</a>` : ""}`;
}

/**
 * 渲染总体业务架构总览页。
 * 无入参和返回值。
 * 核心流程生成五视图切换、交互画布、节点详情和模块快捷入口。
 */
function renderBizOverview() {
  const app = document.querySelector("#bizApp");
  if (!app) return;
  const hashView = window.location.hash.slice(1);
  if (bizViewLabels[hashView]) activeBizOverviewView = hashView;
  app.innerHTML = `
    ${renderBizHeader("总体业务架构")}
    <main class="biz-main biz-overview-page">
      <section class="biz-overview-hero"><div><span class="biz-kicker">SYSTEM RELATIONSHIP / OVERVIEW</span><h1>总体业务架构</h1><p>从物理服务、业务闭环、事实分层和职责边界理解整个平台。</p></div><aside><span>核心原则</span><strong>意图、执行与事实分离</strong><small>物理单体 · 逻辑模块化 · 展示层只读</small></aside></section>
      <nav class="biz-view-switcher" aria-label="总体业务架构视图">${Object.entries(bizViewLabels).map(([id, label]) => `<button type="button" data-biz-overview-view="${id}" aria-pressed="${id === activeBizOverviewView}">${escapeBizHtml(label)}</button>`).join("")}</nav>
      <section class="biz-overview-layout">
        <article class="biz-diagram-panel"><header><span id="bizViewCode">${escapeBizHtml(activeBizOverviewView.toUpperCase())}</span><h2 id="bizViewTitle">${escapeBizHtml(bizViewLabels[activeBizOverviewView])}</h2><p>点击节点查看职责和事实说明</p></header><div id="bizOverviewCanvas">${renderBizOverviewCanvas(activeBizOverviewView)}</div></article>
        <aside id="bizOverviewDetail" class="biz-node-detail" aria-live="polite"><span>阅读方式</span><h3>选择一个节点</h3><p>这里会显示该节点拥有的事实、上下游关系或实施阶段说明。</p></aside>
      </section>
      <section class="biz-overview-modules"><header><span>MODULE DIRECTORY</span><h2>进入模块剖面</h2></header><div>${bizData.modules.map((module) => `<a href="${escapeBizHtml(module.page)}"><span>${escapeBizHtml(module.code)}</span><strong>${escapeBizHtml(module.title)}</strong>${renderBizConfidence(module)}</a>`).join("")}</div></section>
    </main>${renderBizFooter()}
  `;
  bindBizOverviewInteractions();
}

/**
 * 绑定总览页的视图切换与节点详情事件。
 * 无入参和返回值。
 * 核心流程使用事件委托，并同步 URL 哈希和 aria-pressed 状态。
 */
function bindBizOverviewInteractions() {
  document.querySelector(".biz-view-switcher")?.addEventListener("click", (event) => {
    const button = event.target.closest("[data-biz-overview-view]");
    if (!button) return;
    activeBizOverviewView = button.dataset.bizOverviewView;
    window.history.replaceState(null, "", `#${activeBizOverviewView}`);
    document.querySelectorAll("[data-biz-overview-view]").forEach((item) => item.setAttribute("aria-pressed", String(item === button)));
    document.querySelector("#bizViewCode").textContent = activeBizOverviewView.toUpperCase();
    document.querySelector("#bizViewTitle").textContent = bizViewLabels[activeBizOverviewView];
    document.querySelector("#bizOverviewCanvas").innerHTML = renderBizOverviewCanvas(activeBizOverviewView);
    document.querySelector("#bizOverviewDetail").innerHTML = '<span>阅读方式</span><h3>选择一个节点</h3><p>这里会显示该节点拥有的事实、上下游关系或实施阶段说明。</p>';
  });
  document.querySelector("#bizOverviewCanvas")?.addEventListener("click", (event) => {
    const node = event.target.closest("[data-biz-detail-kind]");
    if (node) updateBizOverviewDetail(node.dataset.bizDetailKind, node.dataset.bizDetailId);
  });
}

/**
 * 渲染统一文本清单。
 * 入参为字符串数组和可选样式编码，出参为清单 HTML。
 * 核心流程统一处理边界、异常和扩展内容。
 */
function renderBizList(items, tone = "") {
  return `<ul class="biz-detail-list ${escapeBizHtml(tone)}">${items.map((item) => `<li>${escapeBizHtml(item)}</li>`).join("")}</ul>`;
}

/**
 * 渲染单个业务模块架构页。
 * 入参为模块对象，无返回值。
 * 核心流程展示输入输出、主链、关系、事实归属、边界、异常、扩展和规格来源。
 */
function renderBizModule(module) {
  const app = document.querySelector("#bizApp");
  if (!app) return;
  const flow = module.flow.map((step, index) => `<button type="button" data-biz-module-step="${escapeBizHtml(step.id)}"><span>${String(index + 1).padStart(2, "0")}</span><strong>${escapeBizHtml(step.title)}</strong><small>${escapeBizHtml(step.owner)}</small></button>`).join('<i aria-hidden="true">→</i>');
  const stateAxes = module.stateAxes || [{ label: "关键状态", values: module.states }];
  app.innerHTML = `
    ${renderBizHeader(module.title)}
    <main class="biz-main biz-module-page">
      <section class="biz-module-hero"><div><span class="biz-kicker">${escapeBizHtml(module.code)} / MODULE CUTAWAY</span><div>${renderBizConfidence(module)}<span class="biz-target-label">目标设计 · 接口未实现</span></div><h1>${escapeBizHtml(module.title)}业务架构</h1><p>${escapeBizHtml(module.purpose)}</p></div><aside><span>模块使命</span><strong>${escapeBizHtml(module.tagline)}</strong><small>${escapeBizHtml(module.roles.join(" · "))}</small></aside></section>
      <section class="biz-io-strip">
        <article><span>业务输入</span>${module.inputs.map((item) => `<em>${escapeBizHtml(item)}</em>`).join("")}</article><i aria-hidden="true">→</i>
        <article class="center"><span>事实处理</span><strong>${escapeBizHtml(module.title)}</strong><div class="biz-state-axes">${stateAxes.map((axis) => `<section><span>${escapeBizHtml(axis.label)}</span><small>${axis.values.map((value) => escapeBizHtml(value)).join(" → ")}</small></section>`).join("")}</div></article><i aria-hidden="true">→</i>
        <article><span>业务输出</span>${module.outputs.map((item) => `<em>${escapeBizHtml(item)}</em>`).join("")}</article>
      </section>
      <section id="flow" class="biz-module-flow-section" tabindex="-1">
        <header class="biz-section-head"><div><span>01 / BUSINESS FLOW</span><h2>模块主链</h2></div><p>点击节点核对角色与业务含义</p></header>
        <div class="biz-module-flow">${flow}</div>
        <aside id="bizModuleStepDetail" class="biz-node-detail horizontal" aria-live="polite"><span>当前模块</span><h3>${escapeBizHtml(module.title)}</h3><p>${escapeBizHtml(module.tagline)}</p></aside>
      </section>
      <section id="relations" class="biz-relation-section" tabindex="-1">
        <header class="biz-section-head"><div><span>02 / RELATIONSHIP</span><h2>模块关系与事实归属</h2></div><p>连接靠应用服务与业务引用，不靠跨模块改表</p></header>
        <div class="biz-relation-grid"><article class="biz-owned-facts"><span>本模块拥有的事实</span>${module.ownedFacts.map((item) => `<strong>${escapeBizHtml(item)}</strong>`).join("")}</article>${module.relations.map((relation) => `<article><span>${escapeBizHtml(relation.direction)}</span><h3>${escapeBizHtml(relation.module)}</h3><p>${escapeBizHtml(relation.text)}</p></article>`).join("")}</div>
      </section>
      <section class="biz-control-grid">
        <article id="boundaries" tabindex="-1"><header><span>03</span><h2>职责边界</h2></header>${renderBizList(module.boundaries)}</article>
        <article id="exceptions" class="warning" tabindex="-1"><header><span>04</span><h2>异常控制</h2></header>${renderBizList(module.exceptions, "warning")}</article>
        <article id="extensions" class="future" tabindex="-1"><header><span>05</span><h2>未来扩展</h2></header>${renderBizList(module.extensions, "future")}</article>
      </section>
      <section id="specs" class="biz-spec-links" tabindex="-1"><div><span>FACT SOURCES</span><h2>正式规格依据</h2><p>本模块一期规则已经过逐项讨论确认；页面仍属于目标设计，不代表接口已经实现。</p></div><nav>${module.specs.map((spec) => `<a href="${escapeBizHtml(spec.href)}">${escapeBizHtml(spec.label)} <span>↗</span></a>`).join("")}</nav></section>
    </main>${renderBizFooter()}
  `;
  document.querySelector(".biz-module-flow")?.addEventListener("click", (event) => {
    const button = event.target.closest("[data-biz-module-step]");
    if (!button) return;
    const step = module.flow.find((item) => item.id === button.dataset.bizModuleStep);
    document.querySelector("#bizModuleStepDetail").innerHTML = `<span>${escapeBizHtml(step.owner)}</span><h3>${escapeBizHtml(step.title)}</h3><p>${escapeBizHtml(step.detail)}</p>`;
  });
}

/**
 * 将 URL 哈希对应的总览视图或模块章节置为初始焦点。
 * 无入参和返回值。
 * 核心流程仅接受稳定的英文标识：总览哈希聚焦视图按钮，模块哈希聚焦带 tabindex 的语义章节。
 */
function focusBizHashTarget() {
  const hash = window.location.hash.slice(1);
  if (!hash || !/^[a-zA-Z][a-zA-Z0-9-]*$/.test(hash)) return;
  const target = document.querySelector(`[data-biz-overview-view="${hash}"]`) || document.getElementById(hash);
  target?.focus({ preventScroll: false });
}

/**
 * 初始化业务架构页面。
 * 无入参和返回值。
 * 核心流程根据 body 数据属性选择预览、总览或模块渲染器，并在数据缺失时显示错误。
 */
function initializeBusinessArchitecture() {
  if (!bizData) return;
  if (bizPage === "preview") {
    renderBizPreview();
  } else if (bizPage === "overview") {
    renderBizOverview();
  } else {
    const module = getBizModule(bizModuleId);
    if (module) {
      renderBizModule(module);
    } else {
      const app = document.querySelector("#bizApp");
      if (app) app.innerHTML = '<main class="biz-fatal"><h1>业务架构数据缺失</h1><p>请返回预览页重新选择模块。</p><a href="index.html">返回预览</a></main>';
    }
  }
  // 修改原因：计划要求 URL 可定位到总览视图或模块章节，渲染完成后再聚焦实际目标。
  focusBizHashTarget();
}

document.addEventListener("DOMContentLoaded", initializeBusinessArchitecture);
