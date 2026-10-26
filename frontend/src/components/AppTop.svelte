<script>
  import Brand from './Brand.svelte'
  import { initials } from '../lib/me.svelte.js'
  import { route } from '../lib/router.svelte.js'

  // On a phone the member's initials open Account, which also leads to Group
  const here = $derived(route.name === 'account' || route.name === 'group')
</script>

<div class="app-top">
  <Brand />
  <a class="account-link" href="/account" aria-label="Your account" aria-current={here ? 'page' : undefined}>
    <span class="avatar" aria-hidden="true">{initials()}</span>
  </a>
</div>
