import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // Proxy backend API calls to avoid CORS issues in dev
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // Photon autocomplete API — built on OSM data, designed for rich place autocomplete
      // Much better than Nominatim for autocomplete: returns POIs, roads, suburbs, stations etc.
      '/photon': {
        target: 'https://photon.komoot.io',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/photon/, ''),
      },
      // Nominatim — kept for reverse geocoding (precise locality names from coords)
      '/nominatim': {
        target: 'https://nominatim.openstreetmap.org',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/nominatim/, ''),
        headers: {
          'User-Agent': 'MumbaiDisasterEvacuationSystem/1.0',
        },
      },
    }
  }
})
