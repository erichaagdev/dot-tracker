// The signed-in member's own entries, keyed by book id, and every change to them. A change shows on screen at once,
// worked out with the same rules the backend follows, and then its request goes out. Requests for one book go one
// after another, so quick taps on the page grid never race each other. Each answer is the entry as the backend saved
// it, and a refused change is dropped, so the entry goes back to what was last saved plus any changes still waiting,
// and the refusal's message shows.

import * as api from './api.js'
import { showNotice } from './notice.svelte.js'

export const collection = $state({ status: 'idle', entries: {}, problem: '' })

// The last entry the backend answered with, the changes still waiting, and the queue of requests, for each book
const saved = {}
const waiting = {}
const queues = {}

export async function loadCollection() {
  collection.status = 'loading'
  try {
    const entries = await api.get('/api/collection')
    for (const entry of entries) {
      saved[entry.bookId] = entry
    }
    collection.entries = Object.fromEntries(entries.map((entry) => [entry.bookId, entry]))
    collection.status = 'ready'
  } catch (error) {
    collection.problem = error.message
    collection.status = 'problem'
  }
}

/**
 * Fetches the entries again while the screen stays as it is, as after an admin changes a book's page count, since the
 * backend leaves out pages beyond it. If that fails, the entries on screen stay as they were.
 */
export async function refreshCollection() {
  try {
    const entries = await api.get('/api/collection')
    for (const entry of entries) {
      saved[entry.bookId] = entry
      show(entry.bookId)
    }
  } catch {
    // Nothing to do, the next load picks the change up
  }
}

/** The member's entry for a book, or an untouched one for a book they haven't changed anything about. */
export function entryFor(bookId) {
  return collection.entries[bookId] ?? untouched(bookId)
}

/** Waits until every change to a book that has gone out so far has its answer. */
export function settled(bookId) {
  return queues[bookId] ?? Promise.resolve()
}

export function changeOwned(book, owned) {
  change(book, (entry) => ({ ...entry, owned }), () => api.put(path(book, 'owned'), { owned }))
}

export function changeWanted(book, wanted) {
  change(book, (entry) => ({ ...entry, wanted }), () => api.put(path(book, 'wanted'), { wanted }))
}

export function changeProgress(book, progress) {
  change(book, (entry) => moveTo(entry, progress), () => api.put(path(book, 'progress'), { progress }))
}

/** Marks a page done, or not done when it already is. */
export function togglePage(book, page) {
  if (entryFor(book.id).pagesDone.includes(page)) {
    change(book, (entry) => pageNotDone(entry, page), () => api.del(path(book, `pages/${page}`)))
  } else {
    change(book, (entry) => pageDone(entry, page, book.pageCount), () => api.put(path(book, `pages/${page}`)))
  }
}

/** Sets one of overall, design or color to a rating from 0 to 5, or to null for not rated. */
export function rate(book, which, rating) {
  // The backend sets all three at once, so the request carries the other two as they are when it goes out
  change(
    book,
    (entry) => ({ ...entry, ratings: { ...entry.ratings, [which]: rating } }),
    (entry) => api.put(path(book, 'ratings'), entry.ratings),
  )
}

export function changeNotes(book, notes) {
  const trimmed = notes.trim() || null
  if (trimmed === entryFor(book.id).notes) {
    return
  }
  change(book, (entry) => ({ ...entry, notes: trimmed }), () => api.put(path(book, 'notes'), { notes: trimmed }))
}

function change(book, apply, send) {
  const id = book.id
  const step = { apply }
  ;(waiting[id] ??= []).push(step)
  show(id)
  queues[id] = (queues[id] ?? Promise.resolve()).then(async () => {
    try {
      saved[id] = await send(apply(saved[id] ?? untouched(id)))
    } catch (error) {
      if (error.status !== 401) {
        showNotice(error.message)
      }
    } finally {
      waiting[id].splice(waiting[id].indexOf(step), 1)
      show(id)
    }
  })
}

function show(id) {
  collection.entries[id] = (waiting[id] ?? []).reduce((entry, step) => step.apply(entry), saved[id] ?? untouched(id))
}

function path(book, what) {
  return `/api/collection/${book.id}/${what}`
}

/** An entry for a book nobody has changed anything about, as the backend would answer it. */
export function untouched(bookId) {
  return {
    bookId,
    owned: false,
    wanted: null,
    progress: 'NOT_STARTED',
    pagesDone: [],
    ratings: { overall: null, design: null, color: null },
    notes: null,
    startedOn: null,
    finishedOn: null,
    updatedAt: null,
  }
}

// The same rules as the backend's CollectionEntry, so what shows at once matches what comes back

function moveTo(entry, progress) {
  if (entry.progress === progress) {
    return entry
  }
  switch (progress) {
    case 'NOT_STARTED':
      return { ...entry, progress, startedOn: null, finishedOn: null }
    case 'IN_PROGRESS':
      return { ...entry, progress, startedOn: entry.startedOn ?? today(), finishedOn: null, owned: true }
    case 'FINISHED':
      return { ...entry, progress, finishedOn: today(), owned: true }
  }
}

function pageDone(entry, page, pageCount) {
  if (entry.pagesDone.includes(page)) {
    return entry
  }
  let next = { ...entry, pagesDone: [...entry.pagesDone, page].sort((a, b) => a - b) }
  if (next.progress === 'NOT_STARTED') {
    next = moveTo(next, 'IN_PROGRESS')
  }
  if (next.pagesDone.filter((done) => done <= pageCount).length === pageCount) {
    next = moveTo(next, 'FINISHED')
  }
  return next
}

function pageNotDone(entry, page) {
  if (!entry.pagesDone.includes(page)) {
    return entry
  }
  const next = { ...entry, pagesDone: entry.pagesDone.filter((done) => done !== page) }
  return next.progress === 'FINISHED' ? moveTo(next, 'IN_PROGRESS') : next
}

function today() {
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}
