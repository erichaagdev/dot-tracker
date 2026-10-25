<script>
  import { entryFor, togglePage } from '../lib/collection.svelte.js'

  // Your own pages, which you tap to check off. Given someone else's entry, it shows their pages and can't be changed.
  let { book, entry = undefined } = $props()

  const readOnly = $derived(entry !== undefined)
  const id = $derived(`${book.id}-pages`)
  const pages = $derived(Array.from({ length: book.pageCount }, (_, i) => i + 1))
  const done = $derived(new Set((entry ?? entryFor(book.id)).pagesDone))
</script>

<div class="block">
  <div class="line-h">
    <h2 class="section-title" id={id}>Pages</h2>
    <span class="subtle"><b>{done.size}</b> of {book.pageCount} done</span>
  </div>
  {#if readOnly}
    <ol class="pages read-only" aria-labelledby={id}>
      {#each pages as page (page)}
        <li class="page" class:done={done.has(page)}>
          <span class="visually-hidden">Page </span>{page}<span class="visually-hidden">{done.has(page) ? ', done' : ', not done'}</span>
        </li>
      {/each}
    </ol>
  {:else}
    <div class="pages" role="group" aria-labelledby={id}>
      {#each pages as page (page)}
        <button type="button" class="page" class:done={done.has(page)} aria-pressed={done.has(page)}
          onclick={() => togglePage(book, page)}><span class="visually-hidden">Page </span>{page}</button>
      {/each}
    </div>
  {/if}
</div>
