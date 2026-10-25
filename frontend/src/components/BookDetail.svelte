<script>
  import { onDestroy } from 'svelte'
  import OwnToggle from './OwnToggle.svelte'
  import PageGrid from './PageGrid.svelte'
  import Pill from './Pill.svelte'
  import RatingChoice from './RatingChoice.svelte'
  import { findLine } from '../lib/catalog.svelte.js'
  import { changeNotes, changeProgress, changeWanted, entryFor } from '../lib/collection.svelte.js'
  import { isoDay, lineClass, price, webLink } from '../lib/format.js'
  import { availabilityPill, exclusivePill, pills, wantPill } from '../lib/status.js'

  // Everything about one book and the member's copy of it. The parent keys it by book, so the notes draft starts over
  // for each book.
  let { book } = $props()

  const progressChoices = [
    { value: 'NOT_STARTED', label: 'Not started' },
    { value: 'IN_PROGRESS', label: 'In progress' },
    { value: 'FINISHED', label: 'Finished' },
  ]
  const wantChoices = [
    { value: 'YES', label: 'Yes' },
    { value: 'MAYBE', label: 'Maybe' },
    { value: 'NO', label: 'No' },
  ]

  const entry = $derived(entryFor(book.id))
  const line = $derived(findLine(book.productLineId))
  const headPills = $derived(pills(exclusivePill(book), availabilityPill(book), wantPill(entry)))
  const dates = $derived(datesText(entry))

  let draft = $state('')
  let writing = $state(false)

  // The box follows the saved notes whenever it isn't being typed in, so a refused change shows what's saved
  $effect.pre(() => {
    const notes = entry.notes ?? ''
    if (!writing) {
      draft = notes
    }
  })

  onDestroy(() => {
    if (writing) {
      changeNotes(book, draft)
    }
  })

  function saveNotes() {
    changeNotes(book, draft)
    writing = false
  }

  function datesText({ startedOn, finishedOn }) {
    if (startedOn && finishedOn) {
      return `Started ${isoDay(startedOn)}, finished ${isoDay(finishedOn)}`
    }
    if (startedOn) {
      return `Started ${isoDay(startedOn)}`
    }
    return finishedOn ? `Finished ${isoDay(finishedOn)}` : ''
  }

  function listingText(listing) {
    const parts = [listing.priceCents === null ? '' : price(listing.priceCents), listing.exclusive ? 'Only sold here' : '']
    return parts.filter(Boolean).join(' · ') || 'No price listed'
  }
</script>

<div class="book-head">
  <span class="cover lg {lineClass(line)}" aria-hidden="true"></span>
  <h1>{book.name}</h1>
  <span class="meta">{line?.name} · {book.pageCount} pages</span>
  {#if headPills.length > 0}
    <span class="tags">{#each headPills as pill (pill.text)}<Pill {pill} />{/each}</span>
  {/if}
</div>

<div class="own-row">
  <OwnToggle {book} label="I own this" />
  {#if dates}<span class="subtle">{dates}</span>{/if}
</div>

<div class="choice">
  <div class="line-h">
    <h2 class="choice-label" id="{book.id}-want">Want it?</h2>
    {#if !entry.owned}<small>Yes or maybe puts it on your wishlist</small>{/if}
  </div>
  <div class="seg" role="group" aria-labelledby="{book.id}-want">
    {#each wantChoices as choice (choice.value)}
      <button type="button" class:on={entry.wanted === choice.value} aria-pressed={entry.wanted === choice.value}
        onclick={() => changeWanted(book, entry.wanted === choice.value ? null : choice.value)}>{choice.label}</button>
    {/each}
  </div>
</div>

<div class="choice">
  <h2 class="choice-label" id="{book.id}-progress">Progress</h2>
  <div class="seg" role="group" aria-labelledby="{book.id}-progress">
    {#each progressChoices as choice (choice.value)}
      <button type="button" class:on={entry.progress === choice.value} aria-pressed={entry.progress === choice.value}
        onclick={() => changeProgress(book, choice.value)}>{choice.label}</button>
    {/each}
  </div>
</div>

<PageGrid {book} />

<RatingChoice {book} which="overall" label="Overall" hint="How much you liked the book" />
<RatingChoice {book} which="design" label="Designs" hint="Optional" />
<RatingChoice {book} which="color" label="Colors" hint="Optional" />

<div class="block">
  <h2 class="section-title">Where to buy</h2>
  {#if book.listings.length > 0}
    <div class="card buy-list">
      {#each book.listings as listing, i (i)}
        <div class="buy">
          {#if webLink(listing.url)}
            <a href={webLink(listing.url)} target="_blank" rel="noopener noreferrer">{listing.retailer}</a>
          {:else}
            <strong>{listing.retailer}</strong>
          {/if}
          <span>{listingText(listing)}</span>
        </div>
      {/each}
    </div>
  {:else}
    <p class="subtle">No store is listed for this book yet.</p>
  {/if}
</div>

<div class="block">
  <label class="section-title" for="{book.id}-notes">Your notes</label>
  <textarea class="note" id="{book.id}-notes" bind:value={draft} onfocus={() => (writing = true)} onblur={saveNotes}
    placeholder="Anything you want to remember, like which pens you used"></textarea>
</div>
