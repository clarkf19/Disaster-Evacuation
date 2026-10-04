import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // Same-origin /api in development, mirroring the Vercel rewrite in production.
      // All third-party services (TomTom, Photon, Nominatim, Gemini) are called by the backend.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'node',
  },
})
