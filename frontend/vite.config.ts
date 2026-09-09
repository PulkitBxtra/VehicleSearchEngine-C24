import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // Same-origin in dev, matching how Caddy serves the app in production, so
    // CORS never differs between the two environments.
    proxy: { '/api': 'http://localhost:8080' },
  },
})
