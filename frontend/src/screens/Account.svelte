<script>
  import LogOut from '@lucide/svelte/icons/log-out'
  import AppTop from '../components/AppTop.svelte'
  import { me, signOut } from '../lib/me.svelte.js'
  import { showNotice } from '../lib/notice.svelte.js'

  async function signOutNow() {
    try {
      await signOut()
    } catch (error) {
      showNotice(error.message)
    }
  }
</script>

<AppTop />
<div class="screen-body placeholder">
  <h1 class="page-title">Your account</h1>
  <p>You're signed in as {me.member?.displayName}, {me.member?.email}. More settings are on the way.</p>
  <button type="button" class="btn ghost" onclick={signOutNow}><LogOut class="ic" aria-hidden="true" />Sign out</button>
</div>
