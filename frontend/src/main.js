import { mount } from 'svelte'
import './styles/tokens.css'
import './styles/fonts.css'
import './styles/base.css'
import './styles/controls.css'
import './styles/frame.css'
import './styles/browse.css'
import './styles/book.css'
import './styles/welcome.css'
import App from './App.svelte'

export default mount(App, { target: document.getElementById('app') })
