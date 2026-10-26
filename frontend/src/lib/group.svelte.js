// Everyone in the group and the open invites, for the admin's Group screen. Revoking an invite and changing a role
// show at once and then send their request, and a refused one puts things back and shows why.

import * as api from './api.js'
import { me, signedOutBecause } from './me.svelte.js'
import { showNotice } from './notice.svelte.js'

export const group = $state({ status: 'idle', members: [], invites: [], problem: '' })

/** Loads the members and open invites, keeping what's on screen in place while a fresh copy comes in. */
export async function loadGroup() {
  if (group.status !== 'ready') {
    group.status = 'loading'
  }
  try {
    const [members, invites] = await Promise.all([api.get('/api/members'), api.get('/api/invites')])
    group.members = members
    group.invites = invites
    group.status = 'ready'
  } catch (error) {
    if (error.status !== 401) {
      group.problem = error.message
      group.status = 'problem'
    }
  }
}

/** Makes a new invite for a friend's email, which goes to the top of the open ones, and answers with it. */
export async function createInvite(email) {
  const invite = await api.post('/api/invites', { email })
  group.invites = [invite, ...group.invites]
  return invite
}

export async function revokeInvite(invite) {
  group.invites = group.invites.filter((each) => each.id !== invite.id)
  try {
    await api.del(`/api/invites/${invite.id}`)
  } catch (error) {
    group.invites = [...group.invites, invite].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
    if (error.status !== 401) {
      showNotice(error.message)
    }
  }
}

/**
 * Makes someone an admin or a member. The backend signs them out everywhere, so changing your own role ends your
 * session and shows the sign-in screen.
 */
export async function changeRole(member, role) {
  const before = member.role
  setRole(member.id, role)
  try {
    const changed = await api.put(`/api/members/${member.id}/role`, { role })
    setRole(member.id, changed.role)
    if (member.id === me.member?.id) {
      signedOutBecause('role-changed')
    }
  } catch (error) {
    setRole(member.id, before)
    if (error.status !== 401) {
      showNotice(error.message)
    }
  }
}

function setRole(id, role) {
  group.members = group.members.map((member) => (member.id === id ? { ...member, role } : member))
}
