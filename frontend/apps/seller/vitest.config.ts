import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';

// Dedicated Vitest config for the seller app. Uses jsdom so React component
// tests can render, and mirrors the path aliases from vite.config.ts so test
// imports (`@/...`, `@shared/...`) resolve the same way as the app.
export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      thresholds: {
        statements: 80,
        branches: 80,
        functions: 80,
        lines: 80,
      },
      include: ['src/pages/**/*', 'src/components/**/*', 'src/lib/**/*'],
    },
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
      '@shared': path.resolve(__dirname, '../../shared'),
    },
    // Shared components import react/react-router; force a single copy so
    // hooks and router context are shared with the test renderer (otherwise
    // "Cannot read properties of null (reading 'useContext')").
    dedupe: ['react', 'react-dom', 'react-router', 'react-router-dom', '@tanstack/react-query', 'sonner'],
  },
});
