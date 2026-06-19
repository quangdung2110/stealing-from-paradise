import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
      '@shared': path.resolve(__dirname, '../../shared'),
    },
    // Force packages to resolve from this app's node_modules when imported
    // via ../../shared — prevents "failed to resolve" errors at build time
    dedupe: ['react', 'react-dom', 'react-router-dom', '@tanstack/react-query', 'zustand', 'axios', 'js-cookie', 'sonner'],
  },
  server: {
    port: 3001,
    host: true,
    fs: {
      // Allow serving files from the shared folder (outside app root)
      allow: ['../..'],
    },
    proxy: {
      '/api': {
        target: process.env.VITE_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
    watch: {
      usePolling: true,
      interval: 300,
    },
  },
  optimizeDeps: {
    include: ['react', 'react-dom', 'react-router-dom', '@tanstack/react-query', 'zustand', 'axios', 'js-cookie', 'sonner'],
  },
});
