<script>
  import Check from '@lucide/svelte/icons/check'
  import ChevronLeft from '@lucide/svelte/icons/chevron-left'
  import CircleAlert from '@lucide/svelte/icons/circle-alert'
  import Copy from '@lucide/svelte/icons/copy'
  import Share from '@lucide/svelte/icons/share'
  import UserPlus from '@lucide/svelte/icons/user-plus'
  import X from '@lucide/svelte/icons/x'
  import { onMount, tick } from 'svelte'
  import Loading from '../components/Loading.svelte'
  import { day } from '../lib/format.js'
  import { changeRole, createInvite, group, loadGroup, revokeInvite } from '../lib/group.svelte.js'
  import { me } from '../lib/me.svelte.js'
  import { showNotice } from '../lib/notice.svelte.js'
  import { goBack } from '../lib/router.svelte.js'

  // Inviting friends and choosing who's an admin. Only admins reach this screen.
  const roles = [
    { value: 'MEMBER', label: 'Member' },
    { value: 'ADMIN', label: 'Admin' },
  ]
  const copiedFor = 2000
  const canShare = typeof navigator.share === 'function'

  let email = $state('')
  let creating = $state(false)
  let inviteProblem = $state('')
  let freshId = $state(null)
  let copiedId = $state(null)
  let copiedTimer
  // The role change waiting for a yes, as the member's id and the role they'd get
  let asking = $state(null)

  onMount(() => {
    loadGroup()
    return () => clearTimeout(copiedTimer)
  })

  async function invite(event) {
    event.preventDefault()
    if (!email.trim()) {
      inviteProblem = "Enter your friend's email address."
      return
    }
    inviteProblem = ''
    creating = true
    try {
      freshId = (await createInvite(email)).id
      email = ''
    } catch (error) {
      if (error.status !== 401) {
        inviteProblem = error.message
      }
    } finally {
      creating = false
    }
  }

  async function copy(invite) {
    try {
      await navigator.clipboard.writeText(invite.link)
      copiedId = invite.id
      clearTimeout(copiedTimer)
      copiedTimer = setTimeout(() => (copiedId = null), copiedFor)
    } catch {
      showNotice("This browser wouldn't copy the link. Press and hold the link to copy it yourself.")
    }
  }

  async function share(invite) {
    try {
      await navigator.share({
        title: 'Dot Tracker',
        text: 'Join me on Dot Tracker to keep track of your Lifelines FlowArt books.',
        url: invite.link,
      })
    } catch (error) {
      if (error.name !== 'AbortError') {
        showNotice("Sharing didn't work. Copy the link and send it yourself.")
      }
    }
  }

  async function askToChange(member, role) {
    if (member.role === role) {
      asking = null
      return
    }
    asking = { id: member.id, role }
    await tick()
    document.getElementById(`confirm-role-${member.id}`)?.focus()
  }

  function confirmRole() {
    const member = group.members.find((each) => each.id === asking.id)
    const role = asking.role
    asking = null
    if (member && member.role !== role) {
      changeRole(member, role)
    }
  }

  /** What changing someone's role does, said before it happens, with the words for the button that does it. */
  function roleQuestion(member, role) {
    const name = member.displayName
    if (role === 'ADMIN') {
      return {
        question: `Make ${name} an admin?`,
        detail: `Admins can add and edit books and invite friends. ${name} will be signed out and will be an admin after signing in again.`,
        action: `Make ${name} an admin`,
      }
    }
    if (member.id === me.member?.id) {
      return {
        question: 'Make yourself a member?',
        detail: "You won't be able to add or edit books or invite friends anymore, and you'll be signed out right away.",
        action: 'Make me a member',
      }
    }
    return {
      question: `Make ${name} a member?`,
      detail: `${name} won't be able to add or edit books or invite friends anymore, and will be signed out right away.`,
      action: `Make ${name} a member`,
    }
  }
</script>

<div class="app-top">
  <button type="button" class="back" onclick={() => goBack('/account')}>
    <ChevronLeft class="ic" aria-hidden="true" />Account
  </button>
</div>

{#if group.status === 'problem'}
  <Loading problem={group.problem} retry={loadGroup} />
{:else if group.status !== 'ready'}
  <Loading text="Getting your group…" />
{:else}
  <div class="screen-body">
    <div>
      <h1 class="page-title">Your group</h1>
      <p class="subtle">Invite friends to Dot Tracker and choose who can look after the list.</p>
    </div>

    <section class="block" aria-labelledby="invites-heading">
      <h2 class="section-title" id="invites-heading">Invites</h2>
      <form class="card invite-start" onsubmit={invite} novalidate>
        <p>An invite link lets one friend join with the Google account for their email address. It works once and expires after 14 days.</p>
        <div class="field">
          <label class="field-label" for="invite-email">Your friend's email</label>
          <input class="input" id="invite-email" type="email" inputmode="email" autocomplete="off" autocapitalize="off"
            spellcheck="false" bind:value={email} />
        </div>
        <div role="alert">
          {#if inviteProblem}
            <div class="message-box"><CircleAlert class="ic" aria-hidden="true" /><p>{inviteProblem}</p></div>
          {/if}
        </div>
        <button type="submit" class="btn" disabled={creating}>
          <UserPlus class="ic" aria-hidden="true" />{creating ? 'Making a link…' : 'Invite a friend'}
        </button>
      </form>
      {#if group.invites.length > 0}
        <ul class="list-card">
          {#each group.invites as each (each.id)}
            <li class="list-row invite" class:fresh={each.id === freshId}>
              <span class="txt">
                <strong>{each.id === freshId ? `Your new link for ${each.email}, ready to send` : `For ${each.email}`}</strong>
                <small>Made by {each.createdBy === me.member?.id ? 'you' : each.createdByName} on {day(new Date(each.createdAt))}, expires {day(new Date(each.expiresAt))}</small>
              </span>
              <span class="invite-link">{each.link}</span>
              <span class="row-actions">
                <button type="button" class="btn ghost" onclick={() => copy(each)}>
                  {#if copiedId === each.id}
                    <Check class="ic" aria-hidden="true" />Copied
                  {:else}
                    <Copy class="ic" aria-hidden="true" />Copy link
                  {/if}
                </button>
                {#if canShare}
                  <button type="button" class="btn ghost" onclick={() => share(each)}><Share class="ic" aria-hidden="true" />Share</button>
                {/if}
                <button type="button" class="btn ghost danger" onclick={() => revokeInvite(each)}><X class="ic" aria-hidden="true" />Revoke</button>
              </span>
            </li>
          {/each}
        </ul>
      {:else}
        <p class="subtle">No open invites right now.</p>
      {/if}
      <span class="visually-hidden" role="status">{copiedId ? 'Link copied' : ''}</span>
    </section>

    <section class="block" aria-labelledby="members-heading">
      <h2 class="section-title" id="members-heading">Members</h2>
      <p class="subtle">Admins can add and edit books and invite friends. Changing someone's role signs them out, and they sign in again to pick it up.</p>
      <ul class="list-card">
        {#each group.members as member (member.id)}
          {@const you = member.id === me.member?.id}
          <li class="list-row member">
            <span class="txt">
              <strong>{member.displayName}{#if you}<span class="you">You</span>{/if}</strong>
              <small>{member.email}</small>
            </span>
            <span class="seg" role="group" aria-label="{member.displayName}'s role">
              {#each roles as role (role.value)}
                <button type="button" class:on={member.role === role.value} aria-pressed={member.role === role.value}
                  onclick={() => askToChange(member, role.value)}>{role.label}</button>
              {/each}
            </span>
            {#if you}<small class="hint">Changing your own role signs you out.</small>{/if}
            {#if asking?.id === member.id}
              {@const ask = roleQuestion(member, asking.role)}
              <div class="role-confirm">
                <p><strong>{ask.question}</strong> {ask.detail}</p>
                <div class="role-confirm-actions">
                  <button type="button" class="btn" id="confirm-role-{member.id}" onclick={confirmRole}>{ask.action}</button>
                  <button type="button" class="btn ghost" onclick={() => (asking = null)}>Cancel</button>
                </div>
              </div>
            {/if}
          </li>
        {/each}
      </ul>
    </section>
  </div>
{/if}
