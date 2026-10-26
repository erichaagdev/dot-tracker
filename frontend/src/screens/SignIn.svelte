<script>
  import CircleAlert from '@lucide/svelte/icons/circle-alert'
  import Brand from '../components/Brand.svelte'
  import { route } from '../lib/router.svelte.js'

  // Google sends a refused sign-in back with ?signin=not-invited, an invite link that no longer works lands here with
  // ?signin=invite-expired, and changing your own role ends your session with ?signin=role-changed
  const messages = {
    'not-invited': "That Google account isn't in the group yet, or signing in didn't finish. Ask someone in the group for an invite link, open it, and then continue with Google.",
    'invite-expired': 'This invite has expired or was already used. Ask whoever sent it for a new link.',
    'role-changed': 'Your role changed, so you were signed out. Sign in again to keep going.',
  }

  const message = $derived(messages[route.query.get('signin')])
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
