import path from "path"
import { defineConfig } from 'vite'
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  // DEVELOPMENT ONLY (`npm run dev`). The app calls relative URLs like /api/...
  // (see BACKEND_BASE_URL in src/lib/constants.ts). In production nginx forwards
  // /api/ to the backend container; here the Vite dev server plays nginx's role
  // and forwards /api to the Spring Boot app you run locally.
  // Change the port if your local backend's SERVER_PORT is not 9090.
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:9090',
        changeOrigin: true,
      },
    },
  },
})
