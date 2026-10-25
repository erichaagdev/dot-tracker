<script>
  import OwnToggle from './OwnToggle.svelte'
  import Pill from './Pill.svelte'
  import { entryFor } from '../lib/collection.svelte.js'
  import { lineClass, price } from '../lib/format.js'
  import { availabilityPill, exclusiveText, pills, progressPill, wantPill } from '../lib/status.js'

  // One book in the desktop list table. Clicking the row shows the book in the detail column beside the list.
  let { book, line, selected } = $props()

  let row = $state()

  const entry = $derived(entryFor(book.id))
  const statusPills = $derived(pills(progressPill(book, entry), wantPill(entry), availabilityPill(book)))
  const rating = $derived(entry.ratings.overall)

  $effect(() => {
    if (selected) {
      row.scrollIntoView({ block: 'nearest' })
    }
  })

  function store() {
    if (book.exclusive) {
      return exclusiveText(book)
    }
    const listing = book.listings[0]
    if (!listing) {
      return 'No store listed'
    }
    return listing.priceCents === null ? listing.retailer : `${listing.retailer} · ${price(listing.priceCents)}`
  }
</script>

<div class="trow" class:sel={selected} bind:this={row}>
  <span class="cover {lineClass(line)}" aria-hidden="true"></span>
  <span class="txt">
    <a class="name" href="/books/{book.id}" aria-current={selected ? 'true' : undefined}>{book.name}</a>
    <small>{store()}</small>
  </span>
  <span class="st">
    {#each statusPills as pill (pill.text)}<Pill {pill} />{/each}
    {#if rating !== null}<span class="score">Rated <b>{rating}</b></span>{/if}
  </span>
  <OwnToggle {book} inList />
</div>
