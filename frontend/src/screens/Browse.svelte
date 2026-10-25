<script>
  import ChevronDown from '@lucide/svelte/icons/chevron-down'
  import Pencil from '@lucide/svelte/icons/pencil'
  import AppTop from '../components/AppTop.svelte'
  import BookDetail from '../components/BookDetail.svelte'
  import BookRow from '../components/BookRow.svelte'
  import BookTableRow from '../components/BookTableRow.svelte'
  import SearchField from '../components/SearchField.svelte'
  import { browse, clearFilters, matchesQuery, setLine, setShow, shows } from '../lib/browse.svelte.js'
  import { booksByLine, catalog, findBook, findLine } from '../lib/catalog.svelte.js'
  import { entryFor } from '../lib/collection.svelte.js'
  import { lineClass } from '../lib/format.js'
  import { isAdmin } from '../lib/me.svelte.js'
  import { viewport } from '../lib/viewport.svelte.js'

  // Every book in one list. From 900 px the selected book, from /books/<id>, shows in the detail column.
  let { selectedId = undefined } = $props()

  const linesShownAtFirst = 5

  let everyLine = $state(false)

  const line = $derived(findLine(browse.lineId))
  const show = $derived(shows.find((option) => option.value === browse.show) ?? shows[0])
  const matching = $derived(catalog.books.filter((book) =>
    (!line || book.productLineId === line.id) && show.matches(entryFor(book.id)) && matchesQuery(book, browse.query)))
  const groups = $derived(booksByLine(matching))
  const selected = $derived(selectedId ? findBook(selectedId) : undefined)

  // The desktop filter column counts every book, whatever else is picked
  const showCounts = $derived(Object.fromEntries(shows.map((option) =>
    [option.value, catalog.books.filter((book) => option.matches(entryFor(book.id))).length])))
  const lineCounts = $derived(Object.fromEntries(catalog.lines.map((each) =>
    [each.id, catalog.books.filter((book) => book.productLineId === each.id).length])))
  const listedLines = $derived(everyLine
    ? catalog.lines
    : catalog.lines.filter((each, i) => i < linesShownAtFirst || each.id === line?.id))

  function books(count) {
    return count === 1 ? '1 book' : `${count} books`
  }
</script>

{#snippet nothingMatches()}
  <div class="empty">
    <p>No books match. Try another search or a different filter.</p>
    <button type="button" class="btn ghost" onclick={clearFilters}>Show every book</button>
  </div>
{/snippet}

{#if viewport.wide}
  <div class="desk">
    <aside class="filters" aria-label="Search and filters">
      <SearchField placeholder="Search" />
      <div class="fgroup" role="group" aria-labelledby="show-heading">
        <h3 id="show-heading">Show</h3>
        {#each shows as option (option.value)}
          <button type="button" class="fopt" class:on={show === option} aria-pressed={show === option}
            onclick={() => setShow(option.value)}>{option.option}<span class="n">{showCounts[option.value]}</span></button>
        {/each}
      </div>
      <div class="fgroup" role="group" aria-labelledby="line-heading">
        <h3 id="line-heading">Product line</h3>
        <button type="button" class="fopt" class:on={!line} aria-pressed={!line} onclick={() => setLine('')}>
          All lines<span class="n">{catalog.books.length}</span>
        </button>
        {#each listedLines as each (each.id)}
          <button type="button" class="fopt {lineClass(each)}" class:on={line?.id === each.id} aria-pressed={line?.id === each.id}
            onclick={() => setLine(line?.id === each.id ? '' : each.id)}>
            <span class="sw" aria-hidden="true"></span>{each.name}<span class="n">{lineCounts[each.id]}</span>
          </button>
        {/each}
        {#if listedLines.length < catalog.lines.length}
          <button type="button" class="fopt more" onclick={() => (everyLine = true)}>
            {catalog.lines.length - listedLines.length} more lines
          </button>
        {/if}
      </div>
    </aside>

    <section class="listing" aria-labelledby="listing-heading">
      <div class="listing-h">
        <div>
          <h1 id="listing-heading">{line?.name ?? 'All books'}</h1>
          <div class="subtle">{books(matching.length)}</div>
        </div>
        <span class="subtle">A to Z</span>
      </div>
      {#if matching.length === 0}
        {@render nothingMatches()}
      {:else}
        <div class="table">
          <div class="trow head" aria-hidden="true"><span></span><span>Book</span><span>Status</span><span>Own</span></div>
          {#each groups as group (group.line.id)}
            {#if !line}
              <h2 class="trow group-row {lineClass(group.line)}"><i aria-hidden="true"></i>{group.line.name}</h2>
            {/if}
            {#each group.books as book (book.id)}
              <BookTableRow {book} line={group.line} selected={book.id === selected?.id} />
            {/each}
          {/each}
        </div>
      {/if}
    </section>

    <aside class="detail" aria-label="The book you picked">
      {#if selected}
        {#if isAdmin()}
          <a class="edit-link" href="/books/{selected.id}/edit"><Pencil class="ic" aria-hidden="true" />Edit book</a>
        {/if}
        {#key selected.id}<BookDetail book={selected} />{/key}
      {:else}
        <div class="detail-empty">
          <p>{selectedId ? "We couldn't find that book. It may have been taken off the list." : 'Pick a book from the list to see its pages, ratings and notes here.'}</p>
        </div>
      {/if}
    </aside>
  </div>
{:else}
  <AppTop />
  <div class="browse">
    <SearchField placeholder="Search {books(catalog.books.length)}" />
    <div class="chips" role="group" aria-label="Show">
      {#each shows as option (option.value)}
        <button type="button" class="chip" class:on={show === option} aria-pressed={show === option}
          onclick={() => setShow(option.value)}>{option.chip}</button>
      {/each}
    </div>
    <div class="selects">
      <label class="select" class:set={line}>
        <span>Line <b>{line?.name ?? 'All lines'}</b></span>
        <ChevronDown class="ic sm" aria-hidden="true" />
        <select aria-label="Product line" value={line?.id ?? ''} onchange={(event) => setLine(event.currentTarget.value)}>
          <option value="">All lines</option>
          {#each catalog.lines as each (each.id)}<option value={each.id}>{each.name}</option>{/each}
        </select>
      </label>
    </div>
    <div class="results"><span>{books(matching.length)}</span><span>A to Z</span></div>
    {#if matching.length === 0}
      {@render nothingMatches()}
    {:else}
      <div class="book-list">
        {#each groups as group (group.line.id)}
          {#if !line}
            <h2 class="group-h {lineClass(group.line)}"><i aria-hidden="true"></i>{group.line.name}</h2>
          {/if}
          {#each group.books as book (book.id)}
            <BookRow {book} line={group.line} />
          {/each}
        {/each}
      </div>
    {/if}
  </div>
{/if}
