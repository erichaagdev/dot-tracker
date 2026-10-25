<script>
  import CircleAlert from '@lucide/svelte/icons/circle-alert'
  import { dismissNotice, notice } from '../lib/notice.svelte.js'
</script>

<div role="alert">
  {#if notice.message}
    <div class="notice">
      <CircleAlert class="ic" aria-hidden="true" />
      <p>{notice.message}</p>
      <button type="button" onclick={dismissNotice}>OK</button>
    </div>
  {/if}
</div>
