import { defineConfig } from '@playwright/test';

/**
 * One end-to-end journey against a real API and database (see e2e/journey.spec.ts).
 *
 * Environment:
 *   E2E_BASE_URL          the running UI, for example https://localhost. Unset: Vite is started on :5173 and proxies
 *                         to the API at LIBRO_API_URL (default http://localhost:8080).
 *   E2E_STAFF_ID / E2E_STAFF_PASSWORD   a librarian or library assistant on that API (required)
 *   E2E_BROWSER_CHANNEL   use an installed browser instead of the downloaded one, for example `chrome` or `msedge`
 */
const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:5173';

export default defineConfig({
  testDir: 'e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  // The steps share one student and two copies, so they run in order in a single worker.
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL,
    trace: 'retain-on-failure',
    ...(process.env.E2E_BROWSER_CHANNEL ? { channel: process.env.E2E_BROWSER_CHANNEL } : {}),
  },
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : { command: 'npm run dev -- --port 5173 --strictPort', url: baseURL, reuseExistingServer: true, timeout: 60_000 },
});
