<script>
  // A short wait, or what went wrong with a way to try again
  let { text = 'Getting your books…', problem = '', retry = undefined } = $props()
</script>

<div class="loading">
  {#if problem}
    <p>{problem}</p>
    {#if retry}<button type="button" class="btn" onclick={retry}>Try again</button>{/if}
  {:else}
    <p role="status">{text}</p>
  {/if}
</div>
