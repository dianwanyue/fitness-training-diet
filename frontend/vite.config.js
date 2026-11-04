import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
export default defineConfig({
  plugins: [vue()],
  build: { chunkSizeWarningLimit: 650 },
  server: {
    proxy: { "/api": { target: process.env.FTDMS_API_TARGET || "http://127.0.0.1:8080", changeOrigin: true } },
  },
  preview: {
    proxy: { "/api": { target: process.env.FTDMS_API_TARGET || "http://127.0.0.1:8080", changeOrigin: true } },
  },
});
