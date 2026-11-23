import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The website runs on http://localhost:5173.
// Every call to /api is passed on to the Spring Boot backend on port 8080,
// so the browser sees one address and no extra CORS setup is needed.
// xfwd: the backend is told the real IP address of the computer that made the call (Step 41, attendance).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': { target: 'http://localhost:8080', xfwd: true },
    },
  },
})
