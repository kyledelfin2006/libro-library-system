/// <reference types="vitest/config" />

import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Local development proxies API and probe routes to the Spring Boot app, so the browser sees one origin and
// LIBRO_CORS_ALLOWED_ORIGINS can stay unset (UI execution plan, section 2).
const api = process.env.LIBRO_API_URL ?? 'http://localhost:8080';

// The browser sends Origin: http://localhost:5173 on POST/PUT/PATCH/DELETE. The API has CORS disabled, so it answers
// 403 to any request carrying a foreign Origin. Behind Caddy the UI and API share an origin; the dev proxy must
// look the same, so it drops the header instead of requiring LIBRO_CORS_ALLOWED_ORIGINS.
const sameOrigin = {
  target: api,
  configure: (proxy: { on: (event: 'proxyReq', cb: (req: { removeHeader: (name: string) => void }) => void) => void }) => {
    proxy.on('proxyReq', (req) => req.removeHeader('origin'));
  },
};

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/app': sameOrigin, '/livez': sameOrigin, '/readyz': sameOrigin,
      // API docs exist only when LIBRO_API_DOCS_ENABLED=true; proxied so the Version page can detect and link them.
      '/v3/api-docs': sameOrigin, '/swagger-ui': sameOrigin,
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    // Playwright specs live in e2e/ and run with `npm run test:e2e`, not under Vitest.
    exclude: ['e2e/**', 'node_modules/**'],
  },
});
