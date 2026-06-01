import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const backend = process.env.VITE_DEV_PROXY_TARGET || "http://127.0.0.1:8080";

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    strictPort: false,
    proxy: {
      "/api": { target: backend, changeOrigin: true },
      "/actuator": { target: backend, changeOrigin: true }
    }
  }
});
