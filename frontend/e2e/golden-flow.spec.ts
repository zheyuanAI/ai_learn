import { test, expect, type Page } from "@playwright/test";

/**
 * 阶段 0–7 黄金闭环前置探测套件
 * 
 * 事实依据：docs/superpowers/plans/2026-09-07-stage-0-9-acceptance-repair-plan.md Task 10
 * 注意：本文件当前用于真实页面入口和依赖门禁探测，不把打开页面、打开弹窗或构建成功当作业务事实通过。
 * 只有在每一步提交并复读服务端 ID、数量和状态后，才能把它升级为黄金闭环验收。
 * 业务主线：主数据 -> 销售需求 -> 生产工单 -> 原料采购 -> 到货外观验收 -> 原料质检/放行/上架
 *           -> 领料与工序执行 -> IoT 遥测告警去重 -> 报工/生产质检/成品入库
 *           -> 销售直接拣货与分批出库 -> 采购人工终止未收与全域追溯对账
 */

// 统一测试样本运行号与前缀，避免与正式单据或历史测试数据冲突
const RUN_ID = Date.now().toString(36);
const PREFIX = `UIR0907-${RUN_ID}`;

// 测试上下文环境变量与配置
const BASE_URL = process.env.STAGE_UI_BASE_URL || "http://localhost:5173";
const TENANT_CODE = process.env.STAGE_UI_TENANT_CODE || "tenant_demo_a";
const TENANT_CODE_B = process.env.STAGE_UI_TENANT_CODE_B || "tenant_demo_b";

/**
 * 读取受保护的测试密码；缺少密码时立即失败，避免测试猜测或把认证失败伪装成业务阻塞。
 * 入参：环境变量名称；出参：去除首尾空白的密码字符串；流程：读取并校验非空后返回。
 */
function requiredEnv(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) {
    throw new Error(`${name} 未设置；测试不会猜测或保存密码`);
  }
  return value;
}

const DEFAULT_PASSWORD = requiredEnv("STAGE_UI_PASSWORD");

// 六类正式角色用户名对照
const ROLES = {
  admin: process.env.ROLE_ADMIN || "admin.zhang",
  sales: process.env.ROLE_SALES || "sales.liu",
  purchase: process.env.ROLE_PURCHASE || "buyer.chen",
  warehouse: process.env.ROLE_WAREHOUSE || "wh.operator",
  mes: process.env.ROLE_MES || "mes.inspector",
  iot: process.env.ROLE_IOT || "iot.engineer",
} as const;

/**
 * 辅助函数：执行真实页面角色登录
 * 入参：当前 page 对象、目标角色账号、密码及租户编码
 * 核心流程：输入租户、用户名、密码，提交表单并等待路由跳转出 /login
 */
async function loginAs(page: Page, username: string, password = DEFAULT_PASSWORD, tenant = TENANT_CODE) {
  await page.goto(`${BASE_URL}/login`);
  await page.waitForLoadState("domcontentloaded");

  // 选择/输入租户
  const tenantSelect = page.locator("#loginTenant, select[name='tenantCode']");
  await expect(tenantSelect.first()).toBeVisible();
  await tenantSelect.first().selectOption(tenant);

  // 输入用户名与密码
  await page.fill("#loginUsername, input[name='username']", username);
  await page.fill("#loginPassword, input[name='password']", password);

  // 点击登录并等待会话建立
  await page.click("button[type='submit']");
  await page.waitForURL((url) => !url.pathname.includes("/login"), { timeout: 15000 });
  await expect(page).not.toHaveURL(/\/login(?:\?|$)/);
}

/**
 * 辅助函数：安全注销当前角色会话，避免触发单会话顶替
 */
async function logout(page: Page) {
  try {
    // 先关闭当前页面可能存在的业务弹窗；遮罩层会拦截顶部退出按钮的点击。
    // 入参：当前登录页；出参：无；流程：定位可见弹窗并点击取消/关闭，再执行服务端注销。
    const visibleModal = page.locator(".modal-mask:visible, .modal-overlay:visible, .editor-mask:visible").last();
    if (await visibleModal.count()) {
      const closeButton = visibleModal.getByRole("button", { name: /取消|关闭|✕/i }).first();
      if (await closeButton.count() && await closeButton.isVisible()) {
        await closeButton.click();
      } else {
        await visibleModal.click({ position: { x: 2, y: 2 } });
      }
    }
    const logoutBtn = page.getByRole("button", { name: /退出|登出|注销/i });
    if ((await logoutBtn.count()) > 0 && (await logoutBtn.first().isVisible())) {
      await logoutBtn.first().click();
      await page.waitForURL((url) => url.pathname.includes("/login"), { timeout: 5000 });
    } else {
      // 修改用途：门禁测试可能在跨源错误文档或 about:blank 中提前跳过；仅对本项目页面清理 Web Storage，避免 afterEach 把预期跳过升级成失败。
      const currentUrl = page.url();
      if (currentUrl.startsWith(BASE_URL)) {
        await page.evaluate(() => {
          localStorage.clear();
          sessionStorage.clear();
        });
      }
      await page.goto(`${BASE_URL}/login`);
    }
  } catch {
    // 修改用途：清理动作本身不能覆盖原始门禁结果；错误文档禁止访问 localStorage，导航失败也只保留当前测试结果。
    try {
      const currentUrl = page.url();
      if (currentUrl.startsWith(BASE_URL)) {
        await page.evaluate(() => {
          localStorage.clear();
          sessionStorage.clear();
        });
      }
    } catch {
      // 当前文档不可脚本化时无需再次抛出清理异常。
    }
    try {
      await page.goto(`${BASE_URL}/login`);
    } catch {
      // 页面已处于浏览器错误态时，保留原测试结果并结束清理。
    }
  }
}

test.describe("阶段 0–7 黄金闭环前置探测（不替代业务事实验收）", () => {
  // 共享跨步骤业务实体标识
  const facts = {
    warehouseCode: `WH-${RUN_ID}`,
    uomCode: `UOM-${RUN_ID}`,
    rawMaterialCode: `RAW-${RUN_ID}`,
    finishedGoodCode: `FG-${RUN_ID}`,
    salesOrderNo: "",
    salesOrderLineId: "",
    workOrderNo: "",
    workOrderId: "",
    purchaseOrderNo: "",
    purchaseOrderId: "",
    receiptBatchNo: "",
  };

  test.afterEach(async ({ page }) => {
    // 每个步骤执行完毕后规范登出，确保单有效会话不产生随机 401 顶替
    await logout(page);
  });

  test("步骤 1：主数据入口与表单前置探测（角色: admin.zhang）", async ({ page }) => {
    await loginAs(page, ROLES.admin);

    // 1.1 访问主数据仓库页签（修复 F05：?tab=warehouses 双向绑定）
    await page.goto(`${BASE_URL}/master-data?tab=warehouses`);
    await page.waitForTimeout(500);
    expect(page.url()).toContain("tab=warehouses");

    // 检查仓库维护按钮是否可用
    const addWarehouseBtn = page.getByRole("button", { name: /新增仓库|新建仓库/i });
    await expect(addWarehouseBtn.first()).toBeVisible();
    await addWarehouseBtn.first().click();
    await page.fill("input[name='code'], input[placeholder*='编码']", facts.warehouseCode);
    await page.fill("input[name='name'], input[placeholder*='名称']", `测试仓库-${PREFIX}`);
    const warehouseSubmitBtn = page.getByRole("button", { name: "确认保存", exact: true });
    await expect(warehouseSubmitBtn).toBeVisible();
    await warehouseSubmitBtn.click();
    await page.waitForTimeout(500);

    // 1.2 访问计量单位页签（修复 F05：?tab=uoms）
    await page.goto(`${BASE_URL}/master-data?tab=uoms`);
    await page.waitForTimeout(500);
    expect(page.url()).toContain("tab=uoms");

    const addUomBtn = page.getByRole("button", { name: /新建.*计量单位|新增.*计量单位|新建单位|新增UOM/i });
    await expect(addUomBtn.first()).toBeVisible();
    await addUomBtn.first().click();
    await page.fill("input[name='code'], input[placeholder*='编码']", facts.uomCode);
    await page.fill("input[name='name'], input[placeholder*='名称']", `件-${PREFIX}`);
    await page.getByPlaceholder("如: pcs, kg, m", { exact: true }).fill("PCS");
    const uomSubmitBtn = page.getByRole("button", { name: "确认保存", exact: true });
    await expect(uomSubmitBtn).toBeVisible();
    await uomSubmitBtn.click();
    await page.waitForTimeout(500);

    // 1.3 访问商品物料页签，核对 UOM 必须为下拉选择而非手填 UUID
    await page.goto(`${BASE_URL}/master-data?tab=products`);
    const addProductBtn = page.getByRole("button", { name: /新增商品|新增物料|新建/i });
    await expect(addProductBtn.first()).toBeVisible();
    await addProductBtn.first().click();
    // 验证计量单位下拉框存在且不是手输 UUID
    const uomSelect = page.locator("select[name='uom'], select.form-select");
    await expect(uomSelect.first()).toBeVisible();
    // 关闭弹窗
    await page.keyboard.press("Escape");
  });

  test("步骤 2：销售需求表单前置探测（未提交业务事实，角色: sales.liu）", async ({ page }) => {
    await loginAs(page, ROLES.sales);

    await page.goto(`${BASE_URL}/sales/orders`);
    await page.waitForTimeout(500);
    expect(page.url()).toContain("/sales/orders");

    // 验证销售订单列表可正常加载，不发生白屏或 403 崩溃
    const table = page.locator(".table-container, table, .order-list");
    await expect(table.first()).toBeVisible({ timeout: 5000 });

    // 验证创建销售订单表单入口
    const createBtn = page.getByRole("button", { name: /新建销售订单|新建订单/i });
    await expect(createBtn.first()).toBeVisible();
    await createBtn.first().click();
    // 验证必填字段与商品选择器
    const customerSelect = page.locator("form.modal-body .form-select").nth(0);
    // 修改用途：销售订单契约不持久化仓库，建单页只保留客户与物料两个真实主数据下拉框。
    const productSelect = page.locator("form.modal-body .form-select").nth(1);
    await expect(customerSelect).toBeVisible();
    await expect(productSelect).toBeVisible();
    await page.keyboard.press("Escape");
  });

  test("步骤 3：制造工单表单前置探测（未提交业务事实，角色: mes.inspector）", async ({ page }) => {
    await loginAs(page, ROLES.mes);

    await page.goto(`${BASE_URL}/mes/work-orders`);
    await page.waitForTimeout(500);
    expect(page.url()).toContain("/mes/work-orders");

    // 验证工单列表可正常展示
    const woTable = page.locator(".table-container, table, .work-order-list");
    await expect(woTable.first()).toBeVisible({ timeout: 5000 });

    // 检查新建工单入口
    const createWoBtn = page.getByRole("button", { name: /新建.*工单|创建工单/i });
    await expect(createWoBtn.first()).toBeVisible();
    await createWoBtn.first().click();
    await page.waitForTimeout(300);
    // 确认关闭
    await page.keyboard.press("Escape");
  });

  test("步骤 4：采购订单表单前置探测（未提交业务事实，角色: buyer.chen）", async ({ page }) => {
    await loginAs(page, ROLES.purchase);

    // 修复 F01：验证直接导航与菜单路径均为 /purchasing/orders
    await page.goto(`${BASE_URL}/purchasing/orders`);
    await page.waitForTimeout(500);
    expect(page.url()).toContain("/purchasing/orders");

    // 修复 F10：验证打开新建采购订单时，即使无工单权限，供应商/仓库/物料也不被拖垮为空
    const createPoBtn = page.getByRole("button", { name: /新建采购订单|创建采购单/i });
    await expect(createPoBtn.first()).toBeVisible();
    await createPoBtn.first().click();
    await page.waitForTimeout(400);

    // 必须核对：供应商与仓库选择框不可为空或崩溃态
    const purchaseFormSelects = page.locator("form.modal-body .form-select");
    const supplierSelect = purchaseFormSelects.nth(0);
    const warehouseSelect = purchaseFormSelects.nth(1);
    await expect(supplierSelect).toBeVisible();
    await expect(warehouseSelect).toBeVisible();
    await expect(supplierSelect).toBeEnabled();
    await expect(warehouseSelect).toBeEnabled();

    await page.keyboard.press("Escape");
  });

  test("步骤 5：收货事实门禁（需要真实 Approved 采购单，角色: wh.operator）", async ({ page }) => {
    test.skip(true, "事实验收已迁移到 golden-facts.spec.ts 的动态清洁样本；本旧入口只保留历史前置探测语义");
    await loginAs(page, ROLES.warehouse);

    // 收货工作台使用正式 receipts 路由；订单列表不是仓库角色的收货入口。
    await page.goto(`${BASE_URL}/purchasing/receipts`);
    await page.waitForTimeout(500);
    expect(page.url()).toContain("/purchasing/receipts");

    // 前置步骤没有提交并审核真实采购单，因此此处没有可收货事实；必须失败，不能用 warn 产生绿色结果。
    const receiptActionBtn = page.locator("button.act-receive");
    if ((await receiptActionBtn.count()) === 0) {
      throw new Error("GOLDEN_FLOW_BLOCKED_RECEIPT_FACT: 前置步骤仅探测表单，未获得服务端返回的 Approved purchaseOrderId/lineId");
    }
    await expect(receiptActionBtn.first()).toBeVisible();
  });

  test("步骤 6：质检放行与上架事实门禁（需要 receiptId，角色: mes.inspector & wh.operator）", async ({ page }) => {
    test.skip(true, "事实验收已迁移到 golden-facts.spec.ts，并由服务端返回 receiptId/putawayTaskId");
    await loginAs(page, ROLES.mes);

    // receiptId 必须来自步骤 5 的服务端响应，不能用订单号、行 ID 或随机 UUID 代替。
    await page.goto(`${BASE_URL}/inventory/quality-disposition`);
    await page.waitForTimeout(500);

    throw new Error("GOLDEN_FLOW_BLOCKED_RECEIPT_FACT: 未获得步骤 5 的 receiptId/receiptLineId，不能开始质检放行与上架");
  });

  test("步骤 7：领料、派工与工序执行入口前置探测（未提交业务事实，角色: mes.inspector）", async ({ page }) => {
    await loginAs(page, ROLES.mes);

    // 7.1 验证派工管理页面（修复 F07：级联选择器，无手填 UUID）
    await page.goto(`${BASE_URL}/mes/dispatch`);
    await page.waitForTimeout(500);
    expect(page.url()).toContain("/mes/dispatch");

    const createDispatchBtn = page.getByRole("button", { name: /新建派工单|创建派工/i });
    await expect(createDispatchBtn.first()).toBeVisible();
    await createDispatchBtn.first().click();
    await page.waitForTimeout(300);

    // 核对工单、工序、操作员、设备选择器均为下拉框，禁止文本框盲猜 UUID
    const woSelect = page.locator("select[name='workOrderId'], select.form-select").first();
    await expect(woSelect).toBeVisible();

    await page.keyboard.press("Escape");

    // 7.2 验证工序执行入口
    await page.goto(`${BASE_URL}/mes/operations`);
    await page.waitForTimeout(500);
    expect(page.url()).toContain("/mes/operations");
  });

  test("步骤 8：IoT 遥测与告警事实门禁（需要真实 MQTT，角色: iot.engineer）", async ({ page }) => {
    test.skip(true, "真实 Broker ACL/QoS1 环境仍未注入；不得用 HTTP simulate 替代");
    await loginAs(page, ROLES.iot);

    await page.goto(`${BASE_URL}/iot/devices`);
    await page.waitForTimeout(500);
    expect(page.url()).toContain("/iot/devices");

    await page.goto(`${BASE_URL}/iot/alarms`);
    await page.waitForTimeout(500);
    expect(page.url()).toContain("/iot/alarms");

    // HTTP simulate、页面导航或匿名外部 Broker 均不能替代项目的 QoS1/ACL 证据。
    throw new Error("GOLDEN_FLOW_BLOCKED_MQTT_ENV: 未注入项目 Broker 的 ACL、订阅账号和设备 credential，不能宣称 QoS1 去重通过");
  });

  test("步骤 9：报工、工单质检与成品 FGR 事实门禁（需要真实工序 ID，角色: mes.inspector）", async ({ page }) => {
    test.skip(true, "事实验收已迁移到 golden-facts.spec.ts，并使用响应返回的 operationExecutionId/workReportId/inspectionId");
    await loginAs(page, ROLES.mes);

    await page.goto(`${BASE_URL}/mes/work-orders`);
    await page.waitForTimeout(500);

    throw new Error("GOLDEN_FLOW_BLOCKED_MANUFACTURING_FACT: 未获得 operationExecutionId/workReportId/inspectionId，不能对账报工、质检与 FGR");
  });

  test("步骤 10：销售履约直接拣货与分批出库事实门禁（需要 FGR 库存，角色: wh.operator）", async ({ page }) => {
    test.skip(true, "事实验收已迁移到 golden-facts.spec.ts，并复读两次拣货/发货的服务端累计量");
    await loginAs(page, ROLES.warehouse);

    // 修复 F06：访问 /sales/picks 履约队列
    await page.goto(`${BASE_URL}/sales/picks`);
    await page.waitForTimeout(500);
    expect(page.url()).toContain("/sales/picks");

    throw new Error("GOLDEN_FLOW_BLOCKED_FINISHED_GOODS_FACT: 未获得 FGR 入库事实，不能核对 F onHand/reserved/available 和两次发货");
  });

  test("步骤 11：追溯与看板入口前置探测（未完成数量对账，角色: buyer.chen & admin.zhang）", async ({ page }) => {
    await loginAs(page, ROLES.admin);

    // 访问全域追溯页
    await page.goto(`${BASE_URL}/traceability`);
    await page.waitForTimeout(500);
    expect(page.url()).toContain("/traceability");

    // 访问综合看板
    await page.goto(`${BASE_URL}/dashboard`);
    await page.waitForTimeout(1000);
    expect(page.url()).toContain("/dashboard");
  });

  test("负向与安全性用例：权限隔离与动作守卫核对", async ({ page }) => {
    // 负向 1：tenant.admin 只有 MES 工单查询权限，访问派工写入口应显示无权限且不出现创建按钮。
    await loginAs(page, ROLES.admin);
    await page.goto(`${BASE_URL}/mes/dispatch`);
    await page.waitForTimeout(700);

    const bodyText = await page.locator("body").innerText();
    const isDenied = /没有操作权限|无权访问|无权限|403/.test(bodyText) || page.url().includes("/forbidden") || page.url().includes("/login");
    const createBtn = page.getByRole("button", { name: /新建派工单/ });
    const isCreateVisible = (await createBtn.count()) > 0 && (await createBtn.first().isVisible());

    expect(isDenied).toBeTruthy();
    expect(isCreateVisible).toBeFalsy();

    await logout(page);

    // 负向 2：无 MES 权限的销售角色访问 /mes/work-orders 应被拒绝或受控拦截。
    await loginAs(page, ROLES.sales);
    await page.goto(`${BASE_URL}/mes/work-orders`);
    await page.waitForTimeout(700);

    const salesBodyText = await page.locator("body").innerText();
    const isSalesDenied = /没有操作权限|无权访问|无权限|403/.test(salesBodyText) || page.url().includes("/forbidden") || page.url().includes("/login");
    expect(isSalesDenied).toBeTruthy();
  });
});
