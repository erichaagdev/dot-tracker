// Browse's search and filters, remembered on this device so the list looks the same next time.

import { load, save } from './storage.js'

export const shows = [
  { value: 'all', chip: 'All', option: 'All books', matches: () => true },
  { value: 'own', chip: 'Own', option: 'I own', matches: (entry) => entry.owned },
  { value: 'want', chip: 'Want', option: 'I want', matches: isWanted },
  { value: 'in-progress', chip: 'In progress', option: 'In progress', matches: (entry) => entry.progress === 'IN_PROGRESS' },
  { value: 'finished', chip: 'Finished', option: 'Finished', matches: (entry) => entry.progress === 'FINISHED' },
  { value: 'not-owned', chip: "Don't own", option: "I don't own", matches: (entry) => !entry.owned },
]

const remembered = load('browse', {})

export const browse = $state({
  query: typeof remembered.query === 'string' ? remembered.query : '',
  show: shows.some((show) => show.value === remembered.show) ? remembered.show : 'all',
  lineId: typeof remembered.lineId === 'string' ? remembered.lineId : '',
})

export function setQuery(query) {
  browse.query = query
  remember()
}

export function setShow(show) {
  browse.show = show
  remember()
}

export function setLine(lineId) {
  browse.lineId = lineId
  remember()
}

export function clearFilters() {
  browse.query = ''
  browse.show = 'all'
  browse.lineId = ''
  remember()
}

/** Whether a book is on the wishlist, wanted or maybe wanted and not owned yet. */
export function isWanted(entry) {
  return (entry.wanted === 'YES' || entry.wanted === 'MAYBE') && !entry.owned
}

/** Whether a book's name matches a search, ignoring case, accents and apostrophes. */
export function matchesQuery(book, query) {
  const wanted = simplify(query)
  return wanted === '' || simplify(book.name).includes(wanted)
}

function simplify(text) {
  return text
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .replace(/['’]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase()
}

function remember() {
  save('browse', { query: browse.query, show: browse.show, lineId: browse.lineId })
}
