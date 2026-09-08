import { defineConfig } from "vitest/config";
import vue from "@vitejs/plugin-vue";

/** 单元测试仅收集 src 下测试文件，避免需要环境凭据的 Playwright E2E 用例被 Vitest 执行。 */
export default defineConfig({
  plugins: [vue()],
  test: {
    include: ["src/**/*.{test,spec}.ts"],
  },
});
