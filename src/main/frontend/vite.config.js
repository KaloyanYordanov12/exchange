import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Served by Spring Boot from the jar's static root at '/', so assets resolve from
// an absolute base. During local dev, /pairs, /orders, /candles, /ws etc. are
// proxied to the running backend on :8080 so the SPA talks to real data.
export default defineConfig({
  plugins: [react()],
  base: '/',
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/pairs': 'http://localhost:8080',
      '/book': 'http://localhost:8080',
      '/orders': 'http://localhost:8080',
      '/accounts': 'http://localhost:8080',
      '/register': 'http://localhost:8080',
      '/candles': 'http://localhost:8080',
      '/invariants': 'http://localhost:8080',
      '/admin': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
});
