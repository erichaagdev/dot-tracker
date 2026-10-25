// Everyone in the group and what they have, for People, each person's shelf, their copy of a book and the group
// section on a book. Nothing here changes anyone's books, so each screen fetches a fresh copy when it opens and keeps
// showing the copy it has while that comes in. A refresh that fails leaves that copy on screen.

import * as api from './api.js'

export const people = $state({ status: 'idle', list: [], problem: '' })

// Each person's entries by member id, and everyone's entries for a book by book id, each as { status, data, problem }
export const shelves = $state({})
export const bookEntries = $state({})

export async function loadPeople() {
  if (people.status !== 'ready') {
    people.status = 'loading'
  }
  try {
    people.list = await api.get('/api/people')
    people.status = 'ready'
  } catch (error) {
    if (error.status !== 401 && people.status !== 'ready') {
      people.problem = error.message
      people.status = 'problem'
    }
  }
}

export function findPerson(id) {
  return people.list.find((person) => person.id === id)
}

/** Fetches one person's entries, the same shape as the signed-in member's own. */
export function loadShelf(memberId) {
  return load(shelves, memberId, `/api/people/${memberId}/collection`)
}

/** Fetches everyone's entry for a book, with each person's name and the group's average overall rating. */
export function loadBookEntries(bookId) {
  return load(bookEntries, bookId, `/api/books/${bookId}/entries`)
}

/**
 * Whether someone has anything to show for a book. An entry can be left with nothing in it, such as after unchecking
 * Own, or only say they don't want the book, and then it shows the same as no entry at all.
 */
export function isTracked(entry) {
  return entry.owned
    || entry.wanted === 'YES'
    || entry.wanted === 'MAYBE'
    || entry.progress !== 'NOT_STARTED'
    || entry.pagesDone.length > 0
    || Object.values(entry.ratings).some((rating) => rating !== null)
    || entry.notes !== null
}

async function load(cache, key, path) {
  if (cache[key]?.status !== 'ready') {
    cache[key] = { status: 'loading', data: null, problem: '' }
  }
  try {
    cache[key] = { status: 'ready', data: await api.get(path), problem: '' }
  } catch (error) {
    if (error.status !== 401 && cache[key].status !== 'ready') {
      cache[key] = { status: 'problem', data: null, problem: error.message }
    }
  }
}
