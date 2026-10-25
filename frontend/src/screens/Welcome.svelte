<script>
  import CircleAlert from '@lucide/svelte/icons/circle-alert'
  import { onMount } from 'svelte'
  import Brand from '../components/Brand.svelte'
  import * as api from '../lib/api.js'
  import { day } from '../lib/format.js'

  // Where an invite link lands, before anyone has signed in. The invite waits in the session, and continuing with
  // Google uses it.
  let invite = $state({ status: 'checking', expiresAt: null, message: '' })

  onMount(check)

  async function check() {
    invite = { status: 'checking', expiresAt: null, message: '' }
    try {
      const pending = await api.get('/api/invites/pending')
      invite = { status: 'open', expiresAt: new Date(pending.expiresAt), message: '' }
    } catch (error) {
      invite = { status: error.status === 404 ? 'closed' : 'problem', expiresAt: null, message: error.message }
    }
  }
</script>

<div class="welcome">
  <div class="welcome-card">
    <Brand large />
    <h1>{invite.status === 'closed' ? "This invite can't be used" : "You're invited to Dot Tracker"}</h1>
    <p class="lead">Keep track of your Lifelines FlowArt books. Check off what you own, follow your pages as you go and rate what you finish.</p>
    <span class="cover lg xl line-trace" aria-hidden="true"></span>
    {#if invite.status === 'open'}
      <a class="btn lg" href="/oauth2/authorization/google">Continue with Google</a>
      <p class="fine">This invite works once and expires on {day(invite.expiresAt)}.</p>
    {:else if invite.status === 'checking'}
      <p class="fine" role="status">Checking your invite…</p>
    {:else}
      <div class="message-box" role="alert"><CircleAlert class="ic" aria-hidden="true" /><p>{invite.message}</p></div>
      {#if invite.status === 'problem'}
        <button type="button" class="btn lg" onclick={check}>Try again</button>
      {:else}
        <a class="btn ghost" href="/">Already in the group? Sign in</a>
      {/if}
    {/if}
  </div>
</div>
