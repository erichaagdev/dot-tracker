// Light or dark, picked on Account and remembered on this device, where Device follows the system's setting. The
// script in index.html applies the remembered choice before the first paint, the same way apply does here.

import { load, save } from './storage.js'

export const themes = [
  { value: 'device', label: 'Device' },
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
]

const remembered = load('theme', 'device')

export const theme = $state({
  choice: themes.some((each) => each.value === remembered) ? remembered : 'device',
})

export function setTheme(choice) {
  theme.choice = choice
  save('theme', choice)
  apply(choice)
}

// tokens.css picks the theme from data-theme on <html>. The browser's bar takes the first theme-color that matches, so
// a picked theme puts its own ahead of the two that follow the system, copied from the one for that setting.
function apply(choice) {
  document.querySelector('meta[name="theme-color"]:not([media])')?.remove()
  if (choice === 'device') {
    delete document.documentElement.dataset.theme
    return
  }
  document.documentElement.dataset.theme = choice
  const picked = document.createElement('meta')
  picked.name = 'theme-color'
  picked.content = document.querySelector(`meta[name="theme-color"][media="(prefers-color-scheme: ${choice})"]`).content
  document.querySelector('meta[name="theme-color"]').before(picked)
}
