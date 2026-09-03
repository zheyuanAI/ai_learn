import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { fileURLToPath, URL } from "node:url";

/**
 * Vite 构建与开发服务器配置
 * 1. 配置 @ 路径别名指向 src 目录
 * 2. 配置 /api 反向代理至 Gateway 网关端口 20001
 */
export default defineConfig({
  plugins: [vue()],
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
});
