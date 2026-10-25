<script>
  import Search from '@lucide/svelte/icons/search'
  import { browse, setQuery } from '../lib/browse.svelte.js'

  let { placeholder } = $props()
</script>

<div class="search">
  <label>
    <Search class="ic" aria-hidden="true" />
    <input type="search" {placeholder} aria-label="Search books by name" autocomplete="off" enterkeyhint="search"
      value={browse.query} oninput={(event) => setQuery(event.currentTarget.value)} />
  </label>
  {#if browse.query}
    <button type="button" onclick={() => setQuery('')}>Clear</button>
  {/if}
</div>
