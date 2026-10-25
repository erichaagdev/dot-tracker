<script>
  import ChevronRight from '@lucide/svelte/icons/chevron-right'
  import LogOut from '@lucide/svelte/icons/log-out'
  import Plus from '@lucide/svelte/icons/plus'
  import ShieldCheck from '@lucide/svelte/icons/shield-check'
  import User from '@lucide/svelte/icons/user'
  import Users from '@lucide/svelte/icons/users'
  import AppTop from '../components/AppTop.svelte'
  import Pill from '../components/Pill.svelte'
  import { isAdmin, me, signOut } from '../lib/me.svelte.js'
  import { showNotice } from '../lib/notice.svelte.js'

  // Who's signed in, and for an admin the screens a phone has no top bar to reach
  const role = $derived(isAdmin()
    ? { kind: 'owned', icon: ShieldCheck, text: 'Admin' }
    : { kind: 'quiet', icon: User, text: 'Member' })

  async function signOutNow() {
    try {
      await signOut()
    } catch (error) {
      showNotice(error.message)
    }
  }
</script>

<AppTop />
<div class="screen-body">
  <h1 class="page-title">Your account</h1>

  <dl class="list-card">
    <div class="list-row"><dt>Name</dt><dd>{me.member?.displayName}</dd></div>
    <div class="list-row"><dt>Email</dt><dd>{me.member?.email}</dd></div>
    <div class="list-row"><dt>Role</dt><dd><Pill pill={role} /></dd></div>
  </dl>
  <p class="subtle">
    {isAdmin()
      ? 'As an admin you can add and edit books for everyone and invite friends to the group.'
      : 'To add a book to the list or invite a friend, ask an admin in your group.'}
  </p>

  {#if isAdmin()}
    <section class="block" aria-labelledby="admin-heading">
      <h2 class="section-title" id="admin-heading">For admins</h2>
      <div class="list-card">
        <a class="list-row link" href="/books/new">
          <Plus class="ic" aria-hidden="true" />
          <span class="txt"><strong>Add a book</strong><small>Put a new book in the list for everyone</small></span>
          <ChevronRight class="ic" aria-hidden="true" />
        </a>
        <a class="list-row link" href="/group">
          <Users class="ic" aria-hidden="true" />
          <span class="txt"><strong>Group</strong><small>Invite friends and choose who's an admin</small></span>
          <ChevronRight class="ic" aria-hidden="true" />
        </a>
      </div>
    </section>
  {/if}

  <div>
    <button type="button" class="btn ghost" onclick={signOutNow}><LogOut class="ic" aria-hidden="true" />Sign out</button>
  </div>
</div>
