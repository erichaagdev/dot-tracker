import { createHash } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { defineConfig } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'

const apiTarget = process.env.DOT_TRACKER_API_URL ?? 'http://localhost:8889'
const proxy = Object.fromEntries(['/api', '/join', '/login', '/oauth2', '/logout'].map((path) => [path, apiTarget]))

console.log(`API proxy -> ${apiTarget}`)

// Writes sw.js from src/sw.js with the files that open the app, index.html and everything under assets, and a version
// taken from their contents
function serviceWorker() {
  return {
    name: 'dot-tracker-service-worker',
    apply: 'build',
    enforce: 'post',
    async generateBundle(_, bundle) {
      const names = Object.keys(bundle).filter((name) => name === 'index.html' || name.startsWith('assets/')).sort()
      if (!names.includes('index.html')) {
        this.error('index.html is not in the bundle yet, so sw.js would not open the app')
      }
      const version = createHash('sha256')
      for (const name of names) {
        const output = bundle[name]
        version.update(name).update(output.type === 'chunk' ? output.code : output.source)
      }
      const shell = { version: version.digest('hex').slice(0, 12), files: names.map((name) => `/${name}`) }
      const source = await readFile(new URL('./src/sw.js', import.meta.url), 'utf8')
      this.emitFile({ type: 'asset', fileName: 'sw.js', source: source.replace('self.__SHELL__', JSON.stringify(shell)) })
    },
  }
}

export default defineConfig({
  plugins: [svelte(), serviceWorker()],
  build: {
    outDir: '../build/frontend/static',
    emptyOutDir: true,
  },
  server: { proxy },
  preview: { proxy },
})
