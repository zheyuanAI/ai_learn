import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { fileURLToPath, URL } from "node:url";
import AutoImport from "unplugin-auto-import/vite";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";
import ElementPlus from "unplugin-element-plus/vite";

/**
 * Vite 构建与开发服务器配置
 * 1. 配置 @ 路径别名指向 src 目录
 * 2. 配置 /api 反向代理至 Gateway 网关端口 20001
 * 3. 配置 Element Plus 按需自动导入（组件 + API + 样式）
 */
export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 按需样式补全插件
    ElementPlus({
      useSource: false,
    }),
    // 脚本 API 自动导入
    AutoImport({
      imports: ["vue", "vue-router", "pinia", "@vueuse/core"],
      resolvers: [ElementPlusResolver()],
      dts: "src/auto-imports.d.ts",
    }),
    // 模板组件自动导入
    Components({
      resolvers: [ElementPlusResolver()],
      dts: "src/components.d.ts",
    }),
  ],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:20001",
        changeOrigin: true,
      },
    },
  },
  css: {
    preprocessorOptions: {
      scss: {
        api: "modern-compiler",
      },
    },
  },
});
