<script>
  import { entryFor, togglePage } from '../lib/collection.svelte.js'

  let { book } = $props()

  const id = $derived(`${book.id}-pages`)
  const pages = $derived(Array.from({ length: book.pageCount }, (_, i) => i + 1))
  const done = $derived(new Set(entryFor(book.id).pagesDone))
</script>

<div class="block">
  <div class="line-h">
    <h2 class="section-title" id={id}>Pages</h2>
    <span class="subtle"><b>{done.size}</b> of {book.pageCount} done</span>
  </div>
  <div class="pages" role="group" aria-labelledby={id}>
    {#each pages as page (page)}
      <button type="button" class="page" class:done={done.has(page)} aria-pressed={done.has(page)}
        onclick={() => togglePage(book, page)}><span class="visually-hidden">Page </span>{page}</button>
    {/each}
  </div>
</div>
