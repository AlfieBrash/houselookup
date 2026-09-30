import { defineConfig } from "@playwright/test";
import { fileURLToPath } from "node:url";
import { loadEnv } from "vite";

// Use the backend's variable names, keeping exported environment variables first.
const root = fileURLToPath(new URL(".", import.meta.url));
const backend = fileURLToPath(new URL("./backend", import.meta.url));
const env = { ...loadEnv("test", backend, ""), ...loadEnv("test", root, "") };
for (const name of ["APP_OS_PLACES_API_KEY", "APP_EPC_API_KEY", "STRIPE_SECRET_KEY"]) {
  if (process.env[name] === undefined && env[name] !== undefined) {
    process.env[name] = env[name];
  }
}

export default defineConfig({
  testDir: "./tests/web-services",
  fullyParallel: true,
  workers: 2,
  retries: 0,
  maxFailures: 0,
  timeout: 50_000,
  reporter: "list",
  // These checks use native fetch so credentials never enter Playwright request traces.
  use: { trace: "off", screenshot: "off", video: "off" },
  projects: [
    { name: "web-services", testMatch: "services.spec.ts", outputDir: "test-results/web-services/live" },
    { name: "diagnostics", testMatch: "diagnostics.spec.ts", outputDir: "test-results/web-services/diagnostics" },
  ],
});
