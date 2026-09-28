/**
 * 阶段 0 菜单回归检查函数。
 * 入参为 Playwright CLI 注入的当前页面对象，出参为每条红色断言的结果和最小观测值。
 * 流程只导航和读取真实页面，不创建、修改或删除业务数据。
 */
async (page) => {
  // 解析 URL 的 origin，兼容 Playwright CLI 的受限 run-code 上下文。
  // 入参为当前页面 URL 字符串，出参为协议加主机部分，流程只做字符串切分。
  function getUrlOrigin(url) {
    const value = String(url);
    const schemeEnd = value.indexOf("://");
    const pathStart = value.indexOf("/", schemeEnd >= 0 ? schemeEnd + 3 : 0);
    return pathStart === -1 ? value.split(/[?#]/, 1)[0] : value.slice(0, pathStart);
  }

  // 解析 URL 的 pathname，避免依赖 run-code 未注入的 URL 构造器。
  // 入参为页面 URL 或 waitForURL 回调值，出参为不含查询串和哈希的路径。
  function getUrlPathname(url) {
    const value = String(url);
    const schemeEnd = value.indexOf("://");
    const pathStart = schemeEnd >= 0 ? value.indexOf("/", schemeEnd + 3) : 0;
    if (pathStart === -1) {
      return "/";
    }
    const queryOrHashStart = value.search(/[?#]/, pathStart);
    const pathname = value.slice(pathStart, queryOrHashStart === -1 ? value.length : queryOrHashStart);
    return pathname || "/";
  }

  const origin = getUrlOrigin(page.url());
  const results = [];

  // 读取权限过滤后的真实侧边栏链接，供各角色复用同一套导航断言。
  async function readVisibleMenuPaths() {
    await page.goto(`${origin}/`);
    await page.waitForTimeout(300);
    return page.locator(".nav-list a").evaluateAll((links) =>
      [...new Set(links.map((link) => `${new URL(link.href).pathname}${new URL(link.href).search}`))],
    );
  }

  async function check(id, title, action) {
    try {
      const detail = await action();
      results.push({ id, title, passed: true, detail });
    } catch (error) {
      results.push({
        id,
        title,
        passed: false,
        detail: error instanceof Error ? error.message : String(error),
      });
    }
  }

  // 动态读取当前账号可见菜单叶节点，避免把固定路径列表当成菜单覆盖率证据。
  // 入参：当前登录页面；出参：包含 menuCode、请求路径、最终路径和结果的叶节点记录；流程：读取菜单树、递归过滤后逐页验证。
  async function collectMenuLeaves() {
    const menuResponse = await page.evaluate(async () => {
      const token = localStorage.getItem("ai_learn_token");
      const response = await fetch("/api/me/menus", {
        credentials: "include",
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      const text = await response.text();
      let body = null;
      try {
        body = text ? JSON.parse(text) : null;
      } catch {
        body = { raw: text };
      }
      return { status: response.status, body };
    });

    if (menuResponse.status !== 200) {
      throw new Error(`菜单接口 HTTP ${menuResponse.status}`);
    }

    const root = Array.isArray(menuResponse.body)
      ? menuResponse.body
      : Array.isArray(menuResponse.body?.data)
        ? menuResponse.body.data
        : Array.isArray(menuResponse.body?.data?.items)
          ? menuResponse.body.data.items
          : [];
    const leaves = [];

    function visit(node) {
      if (!node || node.visible === false || node.status === "DISABLED") {
        return;
      }
      const children = Array.isArray(node.children) ? node.children : [];
      const routePath = typeof node.routePath === "string" ? node.routePath.trim() : "";
      if (routePath && children.length === 0) {
        leaves.push({
          menuCode: node.menuCode || node.code || "unknown",
          requestedPath: routePath,
          componentPath: node.componentPath || "",
        });
      }
      children.forEach(visit);
    }

    root.forEach(visit);
    if (leaves.length === 0) {
      throw new Error("/api/me/menus 未返回可验证的可见叶节点");
    }

    const results = [];
    for (const leaf of leaves) {
      const apiResponses = [];
      const onResponse = (response) => {
        if (response.url().includes("/api/")) {
          apiResponses.push({ url: response.url(), status: response.status() });
        }
      };
      page.on("response", onResponse);
      try {
        await page.goto(`${origin}${leaf.requestedPath}`, { waitUntil: "domcontentloaded" });
        await page.waitForTimeout(300);
      } finally {
        page.off("response", onResponse);
      }
      const finalPath = getUrlPathname(page.url());
      const bodyText = await page.locator("body").innerText().catch(() => "");
      const isForbidden = finalPath === "/forbidden" || /403|无权限|无权访问|没有操作权限/.test(bodyText);
      // 业务空状态也可能包含“未找到”，只以正式 404 路由判断页面不存在。
      const isNotFound = finalPath === "/404";
      const result = finalPath === "/" ? "FAIL_SILENT_HOME" : isForbidden ? "FORBIDDEN" : isNotFound ? "NOT_FOUND" : "OK";
      // AI 本轮明确不启动；这里只审计普通业务页面的接口不存在和服务端异常。
      const unavailableApis = leaf.menuCode.startsWith("ai_")
        ? []
        : apiResponses.filter((item) => item.status === 404 || item.status >= 500);
      results.push({
        menuCode: leaf.menuCode,
        requestedPath: leaf.requestedPath,
        finalPath,
        result,
        unavailableApis,
      });
    }

    const routeFailures = results.filter(
      (item) => item.result === "FAIL_SILENT_HOME" || item.result === "NOT_FOUND",
    );
    const unavailableInterfaces = results.filter((item) => item.unavailableApis.length > 0);
    return {
      count: results.length,
      results,
      passed: routeFailures.length === 0 && unavailableInterfaces.length === 0,
    };
  }

  // 登录后页面可能仍在等待用户画像和动态菜单，先等待非登录路由稳定。
  if (getUrlPathname(page.url()) === "/login") {
    await page.waitForURL((url) => getUrlPathname(url) !== "/login", { timeout: 15000 });
  }

  await check("MENU_LEAVES", "动态菜单叶节点必须有明确落点", async () => {
    const report = await collectMenuLeaves();
    if (!report.passed) {
      const failed = report.results.filter(
        (item) =>
          item.result === "FAIL_SILENT_HOME" ||
          item.result === "NOT_FOUND" ||
          item.unavailableApis.length > 0,
      );
      throw new Error(`菜单落点或普通业务接口异常：${JSON.stringify(failed)}`);
    }
    return report;
  });

  await check("F01", "采购菜单点击应进入正式采购订单页", async () => {
    await page.goto(`${origin}/`);
    const purchaseGroup = page.getByRole("button", { name: /采购入库/ });
    if ((await purchaseGroup.count()) === 0) {
      return { skipped: true, reason: "当前角色无采购页面权限，采购分组已隐藏" };
    }
    await purchaseGroup.click();
    const purchaseOrderLink = page.getByRole("link", { name: "采购订单", exact: true });
    if ((await purchaseOrderLink.count()) === 0) {
      return { skipped: true, reason: "当前角色无采购订单查看权限" };
    }
    await purchaseOrderLink.click();
    await page.waitForTimeout(250);

    const actualPath = getUrlPathname(page.url());
    if (actualPath !== "/purchasing/orders") {
      throw new Error(`菜单点击后的路径为 ${actualPath}，预期为 /purchasing/orders`);
    }
    return { actualPath };
  });

  await check("F02", "看板七个接口必须完整可用", async () => {
    const visibleMenuPaths = await readVisibleMenuPaths();
    if (!visibleMenuPaths.includes("/dashboard")) {
      return { skipped: true, reason: "当前角色无看板页面权限" };
    }

    const responses = [];
    const onResponse = (response) => {
      if (response.url().includes("/api/dashboard/")) {
        responses.push({ url: response.url(), status: response.status() });
      }
    };

    page.on("response", onResponse);
    try {
      await page.goto(`${origin}/dashboard`);
      // 前面的菜单巡检可能已把看板结果留在页面内存中；重载后再核对七个真实请求。
      await page.reload({ waitUntil: "domcontentloaded" });
      await page.waitForTimeout(1200);
    } finally {
      page.off("response", onResponse);
    }

    const failedResponses = responses.filter((item) => item.status >= 400);
    const expectedNames = ["inventory", "fulfillment", "manufacturing", "quality", "device", "alarms", "traceability"];
    const missingNames = expectedNames.filter(
      (name) => !responses.some((item) => item.url.includes(`/api/dashboard/${name}`)),
    );
    if (failedResponses.length > 0 || missingNames.length > 0) {
      throw new Error(
        `dashboard 失败响应=${JSON.stringify(failedResponses)}，缺少请求=${missingNames.join(",") || "无"}`,
      );
    }
    return { responseCount: responses.length };
  });

  await check("F11", "看板错误卡片不能标记为实时", async () => {
    const visibleMenuPaths = await readVisibleMenuPaths();
    if (!visibleMenuPaths.includes("/dashboard")) {
      return { skipped: true, reason: "当前角色无看板页面权限" };
    }
    await page.goto(`${origin}/dashboard`);
    await page.waitForTimeout(1000);

    // 修改：错误、陈旧和明确成功的卡片必须使用互斥状态，防止错误结果冒充实时事实。
    const errorCardContainers = page.locator(".summary-card-container").filter({
      has: page.locator(".card-error-body"),
    });
    const errorCards = await errorCardContainers.count();
    const liveBadges = await page.locator(".live-badge").count();
    const unavailableBadges = await page.locator(".unavailable-badge").count();
    for (let index = 0; index < errorCards; index += 1) {
      const errorCard = errorCardContainers.nth(index);
      if ((await errorCard.locator(".live-badge").count()) > 0) {
        throw new Error(`第 ${index + 1} 个错误卡片错误显示“实时”标记`);
      }
      if ((await errorCard.locator(".unavailable-badge").count()) === 0) {
        throw new Error(`第 ${index + 1} 个错误卡片缺少“不可用”标记`);
      }
    }
    return { errorCards, liveBadges, unavailableBadges };
  });

  await check("F03", "侧边栏展示的页面必须均可访问", async () => {
    const visibleMenuPaths = await readVisibleMenuPaths();
    const forbiddenMenuPaths = [];
    for (const menuPath of visibleMenuPaths) {
      await page.goto(`${origin}${menuPath}`);
      await page.waitForTimeout(250);
      if (getUrlPathname(page.url()) === "/forbidden") {
        forbiddenMenuPaths.push(menuPath);
      }
    }
    if (forbiddenMenuPaths.length > 0) {
      throw new Error(`仍展示点击后进入 403 的菜单：${forbiddenMenuPaths.join(",")}`);
    }
    return { visibleMenuCount: visibleMenuPaths.length, forbiddenMenuCount: 0 };
  });

  return {
    passed: results.every((item) => item.passed),
    results,
    finalUrl: page.url(),
  };
}
