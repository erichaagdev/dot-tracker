<script>
  import Pill from './Pill.svelte'
  import { isWanted } from '../lib/browse.svelte.js'
  import { booksByLine, catalog, findBook, findLine } from '../lib/catalog.svelte.js'
  import { initialsOf, lineClass } from '../lib/format.js'
  import { theirOwnPill, theirProgressPill } from '../lib/status.js'

  // A shelf: a count of the collection, the books in progress with their pages, and the finished ones with their
  // ratings. Your own shows the latest finished books. Given a person, it's their shelf, with every book they've
  // finished, everything they own and their wishlist, and each book opens their copy of it.
  let { entries, person = undefined } = $props()

  const recentlyFinishedShown = 5

  // Only entries for books still in the catalog, each with its book and line
  const tracked = $derived(entries
    .map((entry) => ({ entry, book: findBook(entry.bookId) }))
    .filter(({ book }) => book)
    .map((item) => ({ ...item, line: findLine(item.book.productLineId) })))
  const entryByBook = $derived(new Map(tracked.map(({ book, entry }) => [book.id, entry])))

  const owned = $derived(tracked.filter(({ entry }) => entry.owned))
  const finished = $derived(tracked.filter(({ entry }) => entry.progress === 'FINISHED'))
  const wished = $derived(tracked.filter(({ entry }) => isWanted(entry)))

  // The longest running first, so the cards stay put as pages get checked off
  const working = $derived(tracked
    .filter(({ entry }) => entry.progress === 'IN_PROGRESS')
    .sort((a, b) => (a.entry.startedOn ?? '').localeCompare(b.entry.startedOn ?? '') || a.book.name.localeCompare(b.book.name)))

  const finishedShown = $derived(finished
    .toSorted((a, b) => (b.entry.finishedOn ?? '').localeCompare(a.entry.finishedOn ?? ''))
    .slice(0, person ? undefined : recentlyFinishedShown))

  const ownedByLine = $derived(booksByLine(owned.map(({ book }) => book)))
  const wishedByLine = $derived(booksByLine(wished.map(({ book }) => book)))

  function href(book) {
    return person ? `/people/${person.id}/books/${book.id}` : `/books/${book.id}`
  }

  function pagesOf(book, entry) {
    const done = new Set(entry.pagesDone)
    return Array.from({ length: book.pageCount }, (_, i) => done.has(i + 1))
  }
</script>

{#snippet score(entry)}
  {#if entry.ratings.overall === null}
    <span class="score">Not rated</span>
  {:else}
    <span class="score">Rated <b>{entry.ratings.overall}</b></span>
  {/if}
{/snippet}

{#snippet byLine(groups, statusOf)}
  <div class="finished-list by-line">
    {#each groups as group (group.line.id)}
      <h3 class="group-h {lineClass(group.line)}"><i aria-hidden="true"></i>{group.line.name}</h3>
      {#each group.books as book (book.id)}
        {@const entry = entryByBook.get(book.id)}
        {@const status = statusOf(entry)}
        <a class="finished-row" href={href(book)}>
          <span class="cover {lineClass(group.line)}" aria-hidden="true"></span>
          <span class="txt">
            <strong>{book.name}</strong>
            <span class="tags">
              {#if status}<Pill pill={status} />{/if}
              {#if entry.ratings.overall !== null}<span class="score">Rated <b>{entry.ratings.overall}</b></span>{/if}
            </span>
          </span>
        </a>
      {/each}
    {/each}
  </div>
{/snippet}

{#if person}
  <div class="person-title">
    <span class="avatar" aria-hidden="true">{initialsOf(person.displayName)}</span>
    <h1 class="page-title">{person.displayName}'s shelf</h1>
  </div>
{:else}
  <h1 class="page-title">Your shelf</h1>
{/if}

<div class="stats">
  <div class="stat"><b>{owned.length}</b><span>owned of {catalog.books.length}</span></div>
  <div class="stat"><b>{finished.length}</b><span>finished</span></div>
  <div class="stat"><b>{wished.length}</b><span>on wishlist</span></div>
</div>

<section class="block" aria-labelledby="working-heading">
  <h2 class="section-title" id="working-heading">Working on</h2>
  {#if working.length > 0}
    <div class="working">
      {#each working as { book, entry, line } (book.id)}
        <a class="card work" href={href(book)}>
          <span class="cover {lineClass(line)}" aria-hidden="true"></span>
          <strong>{book.name}</strong>
          <span class="meta"><span>{line?.name}</span><span>{entry.pagesDone.length} of {book.pageCount} pages</span></span>
          <span class="dots" aria-hidden="true">
            {#each pagesOf(book, entry) as done, i (i)}<i class:on={done}></i>{/each}
          </span>
        </a>
      {/each}
    </div>
  {:else if person}
    <div class="card empty-card">
      <p>{person.displayName} isn't working on a book right now.</p>
    </div>
  {:else}
    <div class="card empty-card">
      <p>Nothing in progress right now. Open a book you own and check off a page, and it shows up here.</p>
      <a class="btn ghost" href="/browse">Browse books</a>
    </div>
  {/if}
</section>

<section class="block" aria-labelledby="finished-heading">
  <h2 class="section-title" id="finished-heading">{person ? 'Finished' : 'Recently finished'}</h2>
  {#if finishedShown.length > 0}
    <div class="finished-list">
      {#each finishedShown as { book, entry, line } (book.id)}
        <a class="finished-row" href={href(book)}>
          <span class="cover {lineClass(line)}" aria-hidden="true"></span>
          <span class="txt"><strong>{book.name}</strong><small>{line?.name}</small></span>
          {@render score(entry)}
        </a>
      {/each}
    </div>
  {:else if person}
    <div class="card empty-card">
      <p>{person.displayName} hasn't finished a book yet.</p>
    </div>
  {:else}
    <div class="card empty-card">
      <p>No finished books yet. When you finish one, it shows up here with how you rated it.</p>
    </div>
  {/if}
</section>

{#if person}
  <section class="block" aria-labelledby="owned-heading">
    <h2 class="section-title" id="owned-heading">Everything {person.displayName} owns</h2>
    {#if ownedByLine.length > 0}
      {@render byLine(ownedByLine, (entry) => theirProgressPill(findBook(entry.bookId), entry))}
    {:else}
      <div class="card empty-card">
        <p>{person.displayName} hasn't checked off any books yet.</p>
      </div>
    {/if}
  </section>

  <section class="block" aria-labelledby="wishlist-heading">
    <h2 class="section-title" id="wishlist-heading">Wishlist</h2>
    {#if wishedByLine.length > 0}
      {@render byLine(wishedByLine, theirOwnPill)}
    {:else}
      <div class="card empty-card">
        <p>Nothing on {person.displayName}'s wishlist yet.</p>
      </div>
    {/if}
  </section>
{/if}
