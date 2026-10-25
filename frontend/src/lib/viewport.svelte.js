// Whether the window is wide enough for the desktop layout, at the same 900 px the stylesheets switch at.

const wide = window.matchMedia('(min-width: 900px)')

export const viewport = $state({ wide: wide.matches })

wide.addEventListener('change', (event) => {
  viewport.wide = event.matches
})
