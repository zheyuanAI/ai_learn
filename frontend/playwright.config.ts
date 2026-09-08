import { defineConfig, devices } from "@playwright/test";

/**
 * 阶段 0–7 浏览器自动化回归测试配置
 * 统一执行基线：http://localhost:5173
 */
export default defineConfig({
  testDir: "./e2e",
  testMatch: "**/*.spec.ts",
  /* 严格使用 1 个 worker 串行执行，避免多账号或多测试用例并发登录导致单会话顶替 (401) */
  workers: 1,
  fullyParallel: false,
  timeout: 45000,
  expect: {
    timeout: 10000,
  },
  reporter: [
    ["list"],
    [
      "json",
      {
        outputFile: "../../output/playwright/stage-0-7-ui/results.json",
      },
    ],
  ],
  use: {
    baseURL: process.env.STAGE_UI_BASE_URL || "http://localhost:5173",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
