import { expect, test, type Page, type Route } from "@playwright/test";

const baseURL = process.env.STAGE_UI_BASE_URL || "http://localhost:5173";
const username = process.env.STAGE_UI_INSIGHTS_USERNAME || process.env.STAGE_UI_USERNAME;
const password = process.env.STAGE_UI_INSIGHTS_PASSWORD || process.env.STAGE_UI_PASSWORD;
const tenantCode = process.env.STAGE_UI_TENANT_CODE || "tenant_demo_a";

const WORK_ORDER_ID = "11111111-1111-4111-8111-111111111111";
const ALARM_ID = "22222222-2222-4222-8222-222222222222";
const DEVICE_ID = "33333333-3333-4333-8333-333333333333";
const MAP_ID = "44444444-4444-4444-8444-444444444444";

/**
 * 用途：使用环境凭据建立可重复的真实浏览器会话。
 * 入参：Playwright 页面对象；出参：登录后的页面。
 * 流程：凭据缺失时跳过；密码只从进程环境读取，不写入测试文件。
 */
async function login(page: Page): Promise<void> {
  test.skip(!username || !password, "需要 STAGE_UI_USERNAME/STAGE_UI_PASSWORD 才能执行洞察与 IoT 浏览器回归");
  await page.goto(`${baseURL}/login`);
  await page.evaluate(() => {
    localStorage.clear();
    sessionStorage.clear();
  });
  await page.goto(`${baseURL}/login`);
  await page.locator("#loginTenant, select[name='tenantCode']").first().selectOption(tenantCode);
  await page.locator("#loginUsername, input[name='username']").first().fill(username!);
  await page.locator("#loginPassword, input[name='password']").first().fill(password!);
  await page.locator("form.login-form button[type='submit'], button.login-submit-btn").first().click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"));
}

/**
 * 用途：把没有洞察/IoT 权限的账号标记为跳过，而不是把权限边界误报成代码失败。
 * 入参：已登录页面；出参：无。
 */
function skipWhenForbidden(page: Page): void {
  if (new URL(page.url()).pathname === "/forbidden") {
    test.skip(true, "当前账号没有该洞察或 IoT 页面权限，需使用具备对应只读权限的正式账号");
  }
}

/** 返回统一 API 成功信封。 */
function successEnvelope(data: unknown, requestId: string) {
  return {
    code: 200,
    message: "操作成功",
    data,
    request_id: requestId,
  };
}

test("综合看板请求七个正式摘要端点并保留 stale 事实", async ({ page }) => {
  await login(page);

  const calls = new Map<string, string>();
  await page.route("**/api/dashboard/**", async (route) => {
    const url = new URL(route.request().url());
    const type = url.pathname.split("/").pop() || "unknown";
    calls.set(type, url.searchParams.get("time_range") || "");
    const stale = type === "alarms";
    const data: Record<string, unknown> = {
      summary_type: type.toUpperCase(),
      metrics: { total: type === "alarms" ? 3 : 7 },
      time_range: "today",
      source_summary: `${type} facts`,
      generated_at: "2026-09-08T00:00:00Z",
      source_updated_at: "2026-09-07T23:59:00Z",
      stale,
      request_id: `dashboard-${type}`,
    };
    if (stale) data.stale_since = "2026-09-07T23:50:00Z";
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(successEnvelope(data, `dashboard-${type}`)),
    });
  });

  await page.goto(`${baseURL}/dashboard`);
  skipWhenForbidden(page);
  await expect(page.locator(".summary-card-container")).toHaveCount(7);
  await expect(page.getByText("部分陈旧 (1)")).toBeVisible();
  await expect(page.getByText("2026-09-07T23:50:00Z", { exact: true })).toBeVisible();
  expect([...calls.keys()].sort()).toEqual([
    "alarms",
    "device",
    "fulfillment",
    "inventory",
    "manufacturing",
    "quality",
    "traceability",
  ]);
  expect([...calls.values()].every((value) => value === "today")).toBe(true);
});

test("HTTP 模拟器明确标记为开发链路并传递真实去重键", async ({ page }) => {
  await login(page);

  let simulatePayload: any;
  await page.route("**/api/devices**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith("/telemetry")) {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(successEnvelope([], "telemetry-query")) });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(successEnvelope({
        records: [{ id: DEVICE_ID, device_code: "M-REAL-01", device_name: "受控机台", protocol_type: "MQTT" }],
        total: 1,
        page: 1,
        size: 20,
      }, "device-list")),
    });
  });
  await page.route("**/api/protocol-adapters/mqtt/simulate", async (route) => {
    simulatePayload = route.request().postDataJSON();
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(successEnvelope({
        duplicate: true,
        message: "消息已幂等接收",
        messageKey: `${DEVICE_ID}|message_id|same-message-id`,
      }, "mqtt-simulate")),
    });
  });

  await page.goto(`${baseURL}/iot/telemetry`);
  skipWhenForbidden(page);
  await page.getByRole("button", { name: /HTTP QoS 1/ }).click();
  await expect(page.getByText("不代表真实的 Mosquitto MQTT Broker")).toBeVisible();
  const inputs = page.locator(".sim-item input");
  await inputs.nth(0).fill("M-REAL-01");
  await inputs.nth(1).fill("same-message-id");
  await page.getByRole("button", { name: "模拟发送 HTTP QoS 1 消息" }).click();
  await expect(page.getByText("QoS 1 命中幂等去重 (Duplicate = true)")).toBeVisible();
  expect(simulatePayload).toMatchObject({
    device_code: "M-REAL-01",
    message_id: "same-message-id",
  });
});

test("告警详情确认与业务补链使用服务端字段和生命周期事实", async ({ page }) => {
  await login(page);
  let acked = false;
  let ackPayload: any;
  let contextPayload: any;
  const alarmBody = () => ({
    id: ALARM_ID,
    alarm_no: "ALARM-REAL-01",
    device_id: DEVICE_ID,
    device_code: "M-REAL-01",
    device_name: "受控机台",
    alarm_type: "temperature-high",
    alarm_level: "HIGH",
    status: acked ? "Acked" : "Triggered",
    triggered_at: "2026-09-08T10:00:00Z",
    acked_at: acked ? "2026-09-08T10:05:00Z" : null,
    ack_comment: acked ? "现场确认" : null,
    context_status: "Pending",
    metric_code: "temperature",
    trigger_metric_value: "68.5 C",
    trigger_threshold: "> 65 C",
    allowed_actions: [{ action: "ack", enabled: !acked }],
  });

  await page.route(`**/api/device-alarms/${ALARM_ID}`, async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(successEnvelope(alarmBody(), "alarm-detail")) });
  });
  await page.route(`**/api/device-alarms/${ALARM_ID}/ack`, async (route) => {
    ackPayload = route.request().postDataJSON();
    acked = true;
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(successEnvelope(alarmBody(), "alarm-ack")) });
  });
  await page.route(`**/api/device-alarms/${ALARM_ID}/business-context`, async (route) => {
    contextPayload = route.request().postDataJSON();
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(successEnvelope({ alarm_id: ALARM_ID, status: "Linked", detail: "已补链" }, "alarm-context")),
    });
  });

  await page.goto(`${baseURL}/iot/alarms/${ALARM_ID}`);
  skipWhenForbidden(page);
  await expect(page.getByText("ALARM-REAL-01")).toBeVisible();
  await page.getByRole("button", { name: "人工确认此告警" }).click();
  await page.locator("textarea").fill("现场确认");
  await page.getByRole("button", { name: "提交确认" }).click();
  await expect(page.getByText("已确认未恢复")).toBeVisible();
  expect(ackPayload).toEqual({ ack_comment: "现场确认" });

  await page.getByRole("button", { name: "更正/补充上下文" }).click();
  const contextInputs = page.locator(".modal-card input");
  await contextInputs.nth(0).fill(WORK_ORDER_ID);
  await contextInputs.nth(1).fill("55555555-5555-4555-8555-555555555555");
  await page.getByRole("button", { name: "保存软引用" }).click();
  expect(contextPayload).toEqual({
    work_order_id: WORK_ORDER_ID,
    operation_execution_id: "55555555-5555-4555-8555-555555555555",
  });
});

test("追溯、地图投影和异常中心保持 snake_case 与服务端投影事实", async ({ page }) => {
  await login(page);
  let traceUrl = "";
  await page.route("**/api/traceability**", async (route) => {
    traceUrl = route.request().url();
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(successEnvelope({
        nodes: [
          { entity_type: "WORK_ORDER", entity_id: WORK_ORDER_ID, label: "WO-REAL-01", status: "Released", complete: true, source_updated_at: "2026-09-08T10:00:00Z" },
          { entity_type: "DEVICE_ALARM", entity_id: ALARM_ID, label: "ALARM-REAL-01", status: "Pending", complete: false, source_updated_at: "2026-09-08T10:01:00Z" },
        ],
        links: [],
        hidden_node_count: 1,
        missing_sources: ["iot.facts"],
        generated_at: "2026-09-08T10:02:00Z",
        source_updated_at: "2026-09-08T10:01:00Z",
        truncated: true,
      }, "trace-real")),
    });
  });
  await page.goto(`${baseURL}/traceability?entry_type=WORK_ORDER&entity_id=${WORK_ORDER_ID}`);
  skipWhenForbidden(page);
  await expect(page.locator(".node-title").filter({ hasText: "WO-REAL-01" })).toBeVisible();
  await expect(page.getByText("存在 2 处断链缺口")).toBeVisible();
  const traceQuery = new URL(traceUrl).searchParams;
  expect(traceQuery.get("entity_type")).toBe("WORK_ORDER");
  expect(traceQuery.get("entity_id")).toBe(WORK_ORDER_ID);
  expect(traceQuery.get("entityType")).toBeNull();

  await page.route("**/api/site-maps**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/site-maps") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(successEnvelope([{ id: MAP_ID, mapCode: "MAP-REAL-01", mapName: "真实地图", asset: { mimeType: "image/png", storageKey: "maps/real.png" } }], "map-list")),
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(successEnvelope({
        site_map_id: MAP_ID,
        map_code: "MAP-REAL-01",
        map_name: "真实地图",
        background_type: "image/png",
        storage_key: "maps/real.png",
        points: [{ point_id: "66666666-6666-4666-8666-666666666666", entity_type: "DEVICE", entity_id: DEVICE_ID, display_name: "真实机台点位", x_percent: 25, y_percent: 40, rotation: 0, display_status: "Normal", source_updated_at: "2026-09-08T10:00:00Z" }],
        generated_at: "2026-09-08T10:02:00Z",
        request_id: "map-projection",
      }, "map-projection")),
    });
  });
  await page.goto(`${baseURL}/gis/site-maps`);
  skipWhenForbidden(page);
  await expect(page.getByText("MAP-REAL-01")).toBeVisible();
  await page.getByRole("button", { name: "空间监控" }).click();
  await expect(page.getByText("真实地图")).toBeVisible();
  await expect(page.locator(".pin-label").filter({ hasText: "真实机台点位" })).toBeVisible();
  await expect(page.locator(".map-pin")).toHaveAttribute("style", /left: 25%.*top: 40%/);
  await expect(page.getByText("AREA-PROD")).toHaveCount(0);
  await expect(page.getByText("WH-FG-01")).toHaveCount(0);

  let exceptionUrl = "";
  await page.route("**/api/exception-center**", async (route) => {
    exceptionUrl = route.request().url();
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(successEnvelope({
        records: [{ source: "device_alarm", exception_type: "temperature-high", severity: "HIGH", value: "68.5", message: "设备温度超过阈值", occurredAt: "2026-09-08T10:01:00Z" }],
        total: 1,
        page: 1,
        size: 20,
        totalPages: 1,
        generated_at: "2026-09-08T10:02:00Z",
        source_updated_at: "2026-09-08T10:01:00Z",
      }, "exception-real")),
    });
  });
  await page.goto(`${baseURL}/exception-center`);
  skipWhenForbidden(page);
  await expect(page.getByText("设备温度超过阈值")).toBeVisible();
  const exceptionQuery = new URL(exceptionUrl).searchParams;
  expect(exceptionQuery.get("time_range")).toBe("today");
  expect(exceptionQuery.get("page")).toBe("1");
  expect(exceptionQuery.get("size")).toBe("20");
});
