<script>
  import AppTop from '../components/AppTop.svelte'
  import { isWanted } from '../lib/browse.svelte.js'
  import { catalog, findBook, findLine } from '../lib/catalog.svelte.js'
  import { collection } from '../lib/collection.svelte.js'
  import { lineClass } from '../lib/format.js'

  // The home screen: a count of the collection, the books in progress with their pages, and the latest finished ones
  const recentlyFinishedShown = 5

  // Only entries for books still in the catalog, each with its book and line
  const tracked = $derived(Object.values(collection.entries)
    .map((entry) => ({ entry, book: findBook(entry.bookId) }))
    .filter(({ book }) => book)
    .map((item) => ({ ...item, line: findLine(item.book.productLineId) })))

  const owned = $derived(tracked.filter(({ entry }) => entry.owned).length)
  const finished = $derived(tracked.filter(({ entry }) => entry.progress === 'FINISHED'))
  const wished = $derived(tracked.filter(({ entry }) => isWanted(entry)).length)

  // The longest running first, so the cards stay put as pages get checked off
  const working = $derived(tracked
    .filter(({ entry }) => entry.progress === 'IN_PROGRESS')
    .sort((a, b) => (a.entry.startedOn ?? '').localeCompare(b.entry.startedOn ?? '') || a.book.name.localeCompare(b.book.name)))

  const recentlyFinished = $derived(finished
    .toSorted((a, b) => (b.entry.finishedOn ?? '').localeCompare(a.entry.finishedOn ?? ''))
    .slice(0, recentlyFinishedShown))

  function pagesOf(book, entry) {
    const done = new Set(entry.pagesDone)
    return Array.from({ length: book.pageCount }, (_, i) => done.has(i + 1))
  }
</script>

<AppTop />
<div class="screen-body shelf">
  <h1 class="page-title">Your shelf</h1>

  <div class="stats">
    <div class="stat"><b>{owned}</b><span>owned of {catalog.books.length}</span></div>
    <div class="stat"><b>{finished.length}</b><span>finished</span></div>
    <div class="stat"><b>{wished}</b><span>on wishlist</span></div>
  </div>

  <section class="block" aria-labelledby="working-heading">
    <h2 class="section-title" id="working-heading">Working on</h2>
    {#if working.length > 0}
      <div class="working">
        {#each working as { book, entry, line } (book.id)}
          <a class="card work" href="/books/{book.id}">
            <span class="cover {lineClass(line)}" aria-hidden="true"></span>
            <strong>{book.name}</strong>
            <span class="meta"><span>{line?.name}</span><span>{entry.pagesDone.length} of {book.pageCount} pages</span></span>
            <span class="dots" aria-hidden="true">
              {#each pagesOf(book, entry) as done, i (i)}<i class:on={done}></i>{/each}
            </span>
          </a>
        {/each}
      </div>
    {:else}
      <div class="card empty-card">
        <p>Nothing in progress right now. Open a book you own and check off a page, and it shows up here.</p>
        <a class="btn ghost" href="/browse">Browse books</a>
      </div>
    {/if}
  </section>

  <section class="block" aria-labelledby="finished-heading">
    <h2 class="section-title" id="finished-heading">Recently finished</h2>
    {#if recentlyFinished.length > 0}
      <div class="finished-list">
        {#each recentlyFinished as { book, entry, line } (book.id)}
          <a class="finished-row" href="/books/{book.id}">
            <span class="cover {lineClass(line)}" aria-hidden="true"></span>
            <span class="txt"><strong>{book.name}</strong><small>{line?.name}</small></span>
            {#if entry.ratings.overall === null}
              <span class="score">Not rated</span>
            {:else}
              <span class="score">Rated <b>{entry.ratings.overall}</b></span>
            {/if}
          </a>
        {/each}
      </div>
    {:else}
      <div class="card empty-card">
        <p>No finished books yet. When you finish one, it shows up here with how you rated it.</p>
      </div>
    {/if}
  </section>
</div>
