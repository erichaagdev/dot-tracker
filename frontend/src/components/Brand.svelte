<script>
  let { large = false, href = '' } = $props()
</script>

{#if href}
  <a class="brand" class:lg={large} {href}><span class="brand-dots" aria-hidden="true"><i></i><i></i><i></i></span>Dot Tracker</a>
{:else}
  <span class="brand" class:lg={large}><span class="brand-dots" aria-hidden="true"><i></i><i></i><i></i></span>Dot Tracker</span>
{/if}
