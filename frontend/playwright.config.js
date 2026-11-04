import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  use: {
    baseURL: process.env.FTDMS_E2E_URL || "http://127.0.0.1:4179",
    channel: process.env.PLAYWRIGHT_CHANNEL || undefined,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  // An explicit URL lets the same tests verify an already-built preview deployment.
  webServer: process.env.FTDMS_E2E_URL ? undefined : {
    command:
      "node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 4179 --strictPort",
    url: "http://127.0.0.1:4179",
    reuseExistingServer: false,
  },
});
