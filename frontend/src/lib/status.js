// The status pills for a book and the member's entry for it. Each pill is an icon, a word and a color.

import Check from '@lucide/svelte/icons/check'
import CircleCheck from '@lucide/svelte/icons/circle-check'
import CircleOff from '@lucide/svelte/icons/circle-off'
import Clock from '@lucide/svelte/icons/clock'
import Contrast from '@lucide/svelte/icons/contrast'
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
