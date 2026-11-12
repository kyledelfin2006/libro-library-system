/// <reference types="vitest/config" />

import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Local development proxies API and probe routes to the Spring Boot app, so the browser sees one origin and
// LIBRO_CORS_ALLOWED_ORIGINS can stay unset (UI execution plan, section 2).
const api = process.env.LIBRO_API_URL ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: { '/app': api, '/livez': api, '/readyz': api },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
});
