// The status pills for a book and the member's entry for it. Each pill is an icon, a word and a color.

import Check from '@lucide/svelte/icons/check'
import CircleCheck from '@lucide/svelte/icons/circle-check'
import CircleOff from '@lucide/svelte/icons/circle-off'
import Clock from '@lucide/svelte/icons/clock'
import Contrast from '@lucide/svelte/icons/contrast'
import Circle from '@lucide/svelte/icons/circle'
import Heart from '@lucide/svelte/icons/heart'
import Tag from '@lucide/svelte/icons/tag'
import { isWanted } from './browse.svelte.js'

/** Pages done as "x of y" while in progress, finished, or not started for a book that's owned. */
export function progressPill(book, entry) {
  switch (entry.progress) {
    case 'IN_PROGRESS':
      return { kind: 'progress', icon: Contrast, text: `${entry.pagesDone.length} of ${book.pageCount}` }
    case 'FINISHED':
      return { kind: 'done', icon: CircleCheck, text: 'Finished' }
    default:
      return entry.owned ? { kind: 'owned', icon: Check, text: 'Not started' } : null
  }
}

/** Want it, for a book on the wishlist. */
export function wantPill(entry) {
  if (!isWanted(entry)) {
    return null
  }
  return { kind: 'want', icon: Heart, text: entry.wanted === 'MAYBE' ? 'Maybe want it' : 'Want it' }
}

/** Someone else's progress, such as "12 of 20 pages", "Finished" or "Not started" for a book they own. */
export function theirProgressPill(book, entry) {
  switch (entry.progress) {
    case 'IN_PROGRESS':
      return { kind: 'progress', icon: Contrast, text: `${entry.pagesDone.length} of ${book.pageCount} pages` }
    case 'FINISHED':
      return { kind: 'done', icon: CircleCheck, text: 'Finished' }
    default:
      return entry.owned ? { kind: 'quiet', icon: Circle, text: 'Not started' } : null
  }
}

/** Whether someone else owns a book, or else whether they want it. */
export function theirOwnPill(entry) {
  if (entry.owned) {
    return { kind: 'owned', icon: Check, text: 'Owns it' }
  }
  switch (entry.wanted) {
    case 'YES':
      return { kind: 'want', icon: Heart, text: 'Wants it' }
    case 'MAYBE':
      return { kind: 'want', icon: Heart, text: 'Maybe wants it' }
    default:
      return null
  }
}

/** How many books someone owns, is working on and has finished. */
export function countPills({ owned, inProgress, finished }) {
  return [
    { kind: 'owned', icon: Check, text: `${owned} owned` },
    { kind: 'progress', icon: Contrast, text: `${inProgress} in progress` },
    { kind: 'done', icon: CircleCheck, text: `${finished} finished` },
  ]
}

/** Coming soon or no longer sold. A book anyone can buy, or nobody has checked on, has no pill. */
export function availabilityPill(book) {
  switch (book.availability) {
    case 'COMING_SOON':
      return { kind: 'quiet', icon: Clock, text: 'Coming soon' }
    case 'RETIRED':
      return { kind: 'quiet', icon: CircleOff, text: 'No longer sold' }
    default:
      return null
  }
}

/** A store exclusive, named after the store, such as "Target exclusive". */
export function exclusivePill(book) {
  return book.exclusive ? { kind: 'excl', icon: Tag, text: exclusiveText(book) } : null
}

export function exclusiveText(book) {
  const store = book.listings.find((listing) => listing.exclusive)?.retailer
  return store ? `${store} exclusive` : 'Store exclusive'
}

export function pills(...candidates) {
  return candidates.filter(Boolean)
}
