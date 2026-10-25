<script>
  import AppTop from '../components/AppTop.svelte'
  import BookRow from '../components/BookRow.svelte'
  import BookTableRow from '../components/BookTableRow.svelte'
  import { isWanted } from '../lib/browse.svelte.js'
  import { booksByLine, catalog } from '../lib/catalog.svelte.js'
  import { entryFor } from '../lib/collection.svelte.js'
  import { lineClass } from '../lib/format.js'
  import { viewport } from '../lib/viewport.svelte.js'

  // Every book marked want yes or maybe that isn't owned, in Browse's rows and grouped the same way
  const wanted = $derived(catalog.books.filter((book) => isWanted(entryFor(book.id))))
  const groups = $derived(booksByLine(wanted))
</script>

<AppTop />
<div class="screen-body wishlist">
  <div>
    <h1 class="page-title">Your wishlist</h1>
    {#if wanted.length > 0}
      <p class="subtle">{wanted.length === 1 ? '1 book' : `${wanted.length} books`} you want or might want</p>
    {/if}
  </div>

  {#if wanted.length === 0}
    <div class="card empty-card">
      <p>Nothing on your wishlist yet. When a book catches your eye, open it and answer Yes or Maybe to "Want it?", and it shows up here.</p>
      <a class="btn ghost" href="/browse">Browse books</a>
    </div>
  {:else if viewport.wide}
    <div class="table">
      <div class="trow head" aria-hidden="true"><span></span><span>Book</span><span>Status</span><span>Own</span></div>
      {#each groups as group (group.line.id)}
        <h2 class="trow group-row {lineClass(group.line)}"><i aria-hidden="true"></i>{group.line.name}</h2>
        {#each group.books as book (book.id)}
          <BookTableRow {book} line={group.line} selected={false} />
        {/each}
      {/each}
    </div>
  {:else}
    <div class="book-list">
      {#each groups as group (group.line.id)}
        <h2 class="group-h {lineClass(group.line)}"><i aria-hidden="true"></i>{group.line.name}</h2>
        {#each group.books as book (book.id)}
          <BookRow {book} line={group.line} />
        {/each}
      {/each}
    </div>
  {/if}
</div>
