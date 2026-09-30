import { defineConfig, devices } from "@playwright/test";

const frontendUrl = process.env.E2E_FRONTEND_URL ?? "http://localhost:8080";
const frontend = new URL(frontendUrl);
const backendUrl = process.env.VITE_BACKEND_URL ?? "http://localhost:8081";
const backend = new URL(backendUrl);
const frontendPort = frontend.port || (frontend.protocol === "https:" ? "443" : "80");
const backendPort = backend.port || (backend.protocol === "https:" ? "443" : "80");

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: frontendUrl,
    trace: "on-first-retry",
  },
  webServer: [
    {
      command: "mvn -f backend/pom.xml spring-boot:run",
      url: `${backendUrl}/api/auth/me`,
      reuseExistingServer: true,
      timeout: 120_000,
      env: {
        APP_CORS_ALLOWED_ORIGINS: process.env.APP_CORS_ALLOWED_ORIGINS ?? frontendUrl,
        PORT: backendPort,
      },
    },
    {
      command: `npm run dev -- --host ${frontend.hostname} --port ${frontendPort}`,
      url: frontendUrl,
      reuseExistingServer: true,
      env: {
        VITE_BACKEND_URL: backendUrl,
      },
    },
  ],
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
