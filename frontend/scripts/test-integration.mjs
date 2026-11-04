import { spawnSync } from "node:child_process";

const result = spawnSync(process.execPath, ["node_modules/@playwright/test/cli.js", "test", "e2e/integration.spec.js", ...process.argv.slice(2)], {
  stdio: "inherit",
  env: { ...process.env, FTDMS_LIVE_API: "1" },
});
if (result.error) console.error(result.error.message);
process.exit(result.status ?? 1);
