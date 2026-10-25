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

/** Adds a book for everyone, from an admin's details, and answers with the book as the backend saved it. */
export async function addBook(details) {
  const book = await api.post('/api/books', details)
  catalog.books = [...catalog.books, book]
  return book
}

/** Changes a book for everyone, and answers with the book as the backend saved it. */
export async function updateBook(id, details) {
  const book = await api.put(`/api/books/${id}`, details)
  catalog.books = catalog.books.map((each) => (each.id === id ? book : each))
  return book
}

/** Removes a book added by mistake. The backend refuses a book anyone tracks, and then nothing changes here. */
export async function deleteBook(id) {
  await api.del(`/api/books/${id}`)
  catalog.books = catalog.books.filter((each) => each.id !== id)
}

/** Every store the catalog lists, A to Z, for suggesting one as an admin types. */
export function retailers() {
  const names = new Set(catalog.books.flatMap((book) => book.listings.map((listing) => listing.retailer)))
  return [...names].sort(collator.compare)
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
