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

  // 登录后页面可能仍在等待用户画像和动态菜单，先等待非登录路由稳定。
  if (getUrlPathname(page.url()) === "/login") {
    await page.waitForURL((url) => getUrlPathname(url) !== "/login", { timeout: 15000 }).catch(() => {});
  }

  await check("F01", "采购菜单点击应进入正式采购订单页", async () => {
    await page.goto(`${origin}/`);
    await page.getByRole("button", { name: /采购入库/ }).click();
    await page.getByRole("link", { name: "采购订单", exact: true }).click();
    await page.waitForTimeout(250);

    const actualPath = getUrlPathname(page.url());
    if (actualPath !== "/purchasing/orders") {
      throw new Error(`菜单点击后的路径为 ${actualPath}，预期为 /purchasing/orders`);
    }
    return { actualPath };
  });

  await check("F02", "看板七个接口不应返回接口不存在", async () => {
    const responses = [];
    const onResponse = (response) => {
      if (response.url().includes("/api/dashboard/")) {
        responses.push({ url: response.url(), status: response.status() });
      }
    };

    page.on("response", onResponse);
    try {
      await page.goto(`${origin}/dashboard`);
      await page.waitForTimeout(1000);
    } finally {
      page.off("response", onResponse);
    }

    const notFound = responses.filter((item) => item.status === 404);
    const expectedNames = ["inventory", "fulfillment", "manufacturing", "quality", "device", "alarms", "traceability"];
    const missingNames = expectedNames.filter(
      (name) => !responses.some((item) => item.url.includes(`/api/dashboard/${name}`)),
    );
    if (notFound.length > 0 || missingNames.length > 0) {
      throw new Error(
        `dashboard 404=${notFound.length}，缺少请求=${missingNames.join(",") || "无"}`,
      );
    }
    return { responseCount: responses.length };
  });

  await check("F11", "看板错误卡片不能标记为实时", async () => {
    // 修改：错误、陈旧和明确成功的卡片必须使用互斥状态，防止错误结果冒充实时事实。
    const errorCards = await page.locator(".card-error-body").count();
    const liveBadges = await page.locator(".live-badge").count();
    const unavailableBadges = await page.locator(".unavailable-badge").count();
    if (errorCards > 0 && liveBadges > 0) {
      throw new Error(`存在 ${errorCards} 个错误卡片，却显示 ${liveBadges} 个“实时”标记`);
    }
    if (errorCards > 0 && unavailableBadges < errorCards) {
      throw new Error(`错误卡片 ${errorCards} 个，但“不可用”标记仅 ${unavailableBadges} 个`);
    }
    return { errorCards, liveBadges, unavailableBadges };
  });

  await check("F03", "无派工权限时不应展示创建入口", async () => {
    await page.goto(`${origin}/mes/dispatch`);
    await page.waitForTimeout(700);

    const bodyText = await page.locator("body").innerText();
    const denied = bodyText.includes("没有操作权限");
    const createButton = page.getByRole("button", { name: /新建派工单/ });
    const createButtonVisible = (await createButton.count()) > 0 && (await createButton.first().isVisible());

    if (!denied) {
      throw new Error("tenant.admin 未得到预期的派工读取/写入拒绝态");
    }
    if (createButtonVisible) {
      throw new Error("403 无权限页面仍显示“新建派工单”按钮");
    }
    return { denied, createButtonVisible };
  });

  return {
    passed: results.every((item) => item.passed),
    results,
    finalUrl: page.url(),
  };
}
