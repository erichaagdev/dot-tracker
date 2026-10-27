import { mount } from 'svelte'
import './styles/tokens.css'
import './styles/fonts.css'
import './styles/base.css'
import './styles/controls.css'
import './styles/frame.css'
import './styles/browse.css'
import './styles/book.css'
import './styles/shelf.css'
import './styles/editor.css'
import './styles/account.css'
import './styles/people.css'
import './styles/welcome.css'
import App from './App.svelte'

// The service worker opens the app from a cache on later visits, so it shows at once while the backend starts. The
// build writes it, so the dev server has none. Without it, as in some private windows, the app works the same.
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {})
  })
}

export default mount(App, { target: document.getElementById('app') })
