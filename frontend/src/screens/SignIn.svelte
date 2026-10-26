<script>
  import CircleAlert from '@lucide/svelte/icons/circle-alert'
  import { onMount } from 'svelte'
  import Brand from '../components/Brand.svelte'
  import * as api from '../lib/api.js'
  import { route } from '../lib/router.svelte.js'

  // Google sends a refused sign-in back with ?signin=not-invited, or ?signin=wrong-account when the invite was sent to
  // a different email than the Google account's. An invite link that no longer works lands here with
  // ?signin=invite-expired, and changing your own role ends your session with ?signin=role-changed.
  const messages = {
    'not-invited': "That Google account isn't in the group yet, or signing in didn't finish. Ask someone in the group for an invite link, open it, and then continue with Google.",
    'wrong-account': "That Google account isn't the one this invite was sent to. Continue with Google again and pick the account for the email address the invite went to.",
    'invite-expired': 'This invite has expired or was already used. Ask whoever sent it for a new link.',
    'role-changed': 'Your role changed, so you were signed out. Sign in again to keep going.',
  }

  const reason = $derived(route.query.get('signin'))
  // The invite stays in the session after the wrong account, so the message can name the address it's for
  let inviteEmail = $state('')
  const message = $derived(
    reason === 'wrong-account' && inviteEmail
      ? `This invite is for ${inviteEmail}, and that Google account has a different email address. Continue with Google again and pick the account for ${inviteEmail}.`
      : messages[reason],
  )

  onMount(async () => {
    if (reason === 'wrong-account') {
      try {
        inviteEmail = (await api.get('/api/invites/pending')).email
      } catch {
        // The general message already says what to do
      }
    }
  })
</script>

<div class="welcome">
  <div class="welcome-card">
    <Brand large />
    <h1>Sign in to Dot Tracker</h1>
    <p class="lead">Keep track of your Lifelines FlowArt books. Check off what you own, follow your pages as you go and rate what you finish.</p>
    {#if message}
      <div class="message-box" role="alert"><CircleAlert class="ic" aria-hidden="true" /><p>{message}</p></div>
    {/if}
    <span class="cover lg xl line-trace" aria-hidden="true"></span>
    <a class="btn lg" href="/oauth2/authorization/google">Continue with Google</a>
    <p class="fine">Dot Tracker is for a small group of friends. To join, ask someone in the group for an invite link.</p>
    <p class="fine"><a href="/privacy">Privacy</a></p>
  </div>
</div>
