<script>
  import Eye from '@lucide/svelte/icons/eye'
  import { onDestroy } from 'svelte'
  import BookGroup from './BookGroup.svelte'
  import OwnToggle from './OwnToggle.svelte'
  import PageGrid from './PageGrid.svelte'
  import Pill from './Pill.svelte'
  import RatingChoice from './RatingChoice.svelte'
  import { findLine } from '../lib/catalog.svelte.js'
  import { changeNotes, changeProgress, changeWanted, entryFor } from '../lib/collection.svelte.js'
  import { initialsOf, isoDay, lineClass, price, webLink } from '../lib/format.js'
  import { availabilityPill, exclusivePill, pills, theirOwnPill, theirProgressPill, wantPill } from '../lib/status.js'

  // Everything about one book and the member's copy of it, with the rest of the group's copies below. Given a person
  // and their entry, it shows that person's copy instead, which can't be changed. The parent keys it by book, so the
  // notes draft starts over for each book.
  let { book, person = undefined, entry: theirs = undefined } = $props()

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

  const readOnly = $derived(person !== undefined)
  const entry = $derived(readOnly ? theirs : entryFor(book.id))
  const line = $derived(findLine(book.productLineId))
  const headPills = $derived(pills(exclusivePill(book), availabilityPill(book), readOnly ? null : wantPill(entry)))
  const theirPills = $derived(readOnly ? pills(theirProgressPill(book, entry), theirOwnPill(entry)) : [])
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
    if (writing && !readOnly) {
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

{#if readOnly}
  <div class="their-copy">
    <span class="avatar" aria-hidden="true">{initialsOf(person.displayName)}</span>
    <span class="txt">
      <strong>{person.displayName}'s copy</strong>
      <small>Only {person.displayName} can change it.</small>
    </span>
    <a href="/people/{person.id}">{person.displayName}'s shelf</a>
  </div>
{/if}

<div class="book-head">
  <span class="cover lg {lineClass(line)}" aria-hidden="true"></span>
  <h1>{book.name}</h1>
  <span class="meta">{line?.name} · {book.pageCount} pages</span>
  {#if headPills.length > 0}
    <span class="tags">{#each headPills as pill (pill.text)}<Pill {pill} />{/each}</span>
  {/if}
</div>

{#if readOnly}
  <div class="own-row">
    {#each theirPills as pill (pill.text)}<Pill {pill} />{:else}<span class="subtle">Not on {person.displayName}'s list yet</span>{/each}
    {#if dates}<span class="subtle">{dates}</span>{/if}
  </div>
{:else}
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
{/if}

<PageGrid {book} entry={theirs} />

{#if readOnly}
  <div class="block">
    <h2 class="section-title">Ratings</h2>
    <div class="card their-ratings">
      <RatingChoice {book} which="overall" label="Overall" entry={theirs} />
      <RatingChoice {book} which="design" label="Designs" entry={theirs} />
      <RatingChoice {book} which="color" label="Colors" entry={theirs} />
    </div>
  </div>
{:else}
  <RatingChoice {book} which="overall" label="Overall" hint="How much you liked the book" />
  <RatingChoice {book} which="design" label="Designs" hint="Optional" />
  <RatingChoice {book} which="color" label="Colors" hint="Optional" />
{/if}

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

{#if readOnly}
  <div class="block">
    <h2 class="section-title">{person.displayName}'s notes</h2>
    {#if entry.notes}
      <p class="card their-note">{entry.notes}</p>
    {:else}
      <p class="subtle">{person.displayName} hasn't written any notes about this book.</p>
    {/if}
  </div>
{:else}
  <div class="block">
    <label class="section-title" for="{book.id}-notes">Your notes</label>
    <p class="note-hint" id="{book.id}-notes-hint"><Eye class="ic sm" aria-hidden="true" />Everyone in your group can read your notes.</p>
    <textarea class="note" id="{book.id}-notes" bind:value={draft} onfocus={() => (writing = true)} onblur={saveNotes}
      aria-describedby="{book.id}-notes-hint" placeholder="Anything you want to remember, like which pens you used"></textarea>
  </div>

  <BookGroup {book} />
{/if}
