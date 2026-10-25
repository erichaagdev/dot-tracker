import { defineConfig } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'

const apiTarget = process.env.DOT_TRACKER_API_URL ?? 'http://localhost:8889'
const proxy = Object.fromEntries(['/api', '/join', '/login', '/oauth2', '/logout'].map((path) => [path, apiTarget]))

console.log(`API proxy -> ${apiTarget}`)

export default defineConfig({
  plugins: [svelte()],
  build: {
    outDir: '../build/frontend/static',
    emptyOutDir: true,
  },
  server: { proxy },
  preview: { proxy },
})
