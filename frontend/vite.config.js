import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
const proxy = {
  '/review-api/decision': { target: 'http://localhost:8084', changeOrigin: true, rewrite: path => path.replace('/review-api/decision', '') },
  '/review-api/verification': { target: 'http://localhost:8083', changeOrigin: true, rewrite: path => path.replace('/review-api/verification', '') },
  '/review-api/document': { target: 'http://localhost:8082', changeOrigin: true, rewrite: path => path.replace('/review-api/document', '') },
};
export default defineConfig({
  plugins: [react()],
  server: { proxy },
  preview: { proxy },
})
