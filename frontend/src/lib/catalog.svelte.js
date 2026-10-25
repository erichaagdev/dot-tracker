// The shared catalog, every book with its listings and every product line, loaded once after sign-in. It's about a
// hundred books, so screens search and filter it here rather than asking the backend.

import * as api from './api.js'

export const catalog = $state({ status: 'idle', books: [], lines: [], problem: '' })

export async function loadCatalog() {
  catalog.status = 'loading'
  try {
    const [books, lines] = await Promise.all([api.get('/api/books'), api.get('/api/product-lines')])
    catalog.books = books
    catalog.lines = lines
    catalog.status = 'ready'
  } catch (error) {
    catalog.problem = error.message
    catalog.status = 'problem'
  }
}

export function findBook(id) {
  return catalog.books.find((book) => book.id === id)
}

export function findLine(id) {
  return catalog.lines.find((line) => line.id === id)
}

/** Every product line in its sort order, each with its books from A to Z. */
export function booksByLine(books = catalog.books) {
  return catalog.lines
    .map((line) => ({
      line,
      books: books.filter((book) => book.productLineId === line.id).sort(byName),
    }))
    .filter((group) => group.books.length > 0)
}

const collator = new Intl.Collator('en', { sensitivity: 'base', numeric: true })

function byName(a, b) {
  return collator.compare(a.name, b.name)
}
