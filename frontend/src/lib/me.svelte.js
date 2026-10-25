// The signed-in member, from GET /api/me. Any request answered with a 401 marks the app signed out, which shows the
// sign-in screen.

import * as api from './api.js'
import { navigate } from './router.svelte.js'

export const me = $state({ status: 'idle', member: null, problem: '' })

api.onSignedOut(() => {
  me.status = 'signed-out'
  me.member = null
})

export async function loadMe() {
  me.status = 'loading'
  try {
    me.member = await api.get('/api/me')
    me.status = 'signed-in'
  } catch (error) {
    if (error.status !== 401) {
      me.problem = error.message
      me.status = 'problem'
    }
  }
}

export async function signOut() {
  try {
    await api.post('/api/logout')
  } catch (error) {
    if (error.status !== 401) {
      throw error
    }
  }
  me.member = null
  me.status = 'signed-out'
  navigate('/', { replace: true })
}

export function isAdmin() {
  return me.member?.role === 'ADMIN'
}

/** One or two letters for the member's avatar, from their display name. */
export function initials() {
  const words = (me.member?.displayName ?? '').trim().split(/\s+/).filter(Boolean)
  const letters = words.length > 1 ? words[0][0] + words.at(-1)[0] : (words[0]?.[0] ?? '?')
  return letters.toUpperCase()
}
