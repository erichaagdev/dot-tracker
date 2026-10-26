<script>
  let { large = false, href = '' } = $props()

  // The mark is a spiral pad like the books, with its page of dots partway colored in, one color per line family
  const dots = ['line-dbl', 'line-mini', 'line-glow', 'line-trace', 'line-pop', 'line-reveal', 'todo', 'todo', 'todo']
  const coils = [8.4, 11.44, 14.48, 17.52, 20.56, 23.6]
</script>

{#snippet mark()}
  <svg class="brand-mark" viewBox="0 0 32 32" aria-hidden="true">
    <rect class="page" x="5" y="6.4" width="22" height="23.6" rx="2.6" />
    {#each dots as dot, i (i)}
      <circle class={dot} cx={10 + (i % 3) * 6} cy={13.4 + Math.floor(i / 3) * 6} r="2.3" />
    {/each}
    {#each coils as x (x)}
      <rect class="coil" x={x - 0.85} y="3" width="1.7" height="6.4" rx="0.85" />
    {/each}
  </svg>
{/snippet}

{#if href}
  <a class="brand" class:lg={large} {href}>{@render mark()}Dot Tracker</a>
{:else}
  <span class="brand" class:lg={large}>{@render mark()}Dot Tracker</span>
{/if}
