<script>
  import Brand from './Brand.svelte'
  import { initials, me } from '../lib/me.svelte.js'
</script>

<div class="app-top">
  <Brand />
  <span class="avatar" title="Signed in as {me.member?.displayName}" aria-hidden="true">{initials()}</span>
</div>
