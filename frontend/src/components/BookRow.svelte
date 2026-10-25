<script>
  import OwnToggle from './OwnToggle.svelte'
  import Pill from './Pill.svelte'
  import { entryFor } from '../lib/collection.svelte.js'
  import { lineClass } from '../lib/format.js'
  import { availabilityPill, exclusivePill, pills, progressPill, wantPill } from '../lib/status.js'

  // One book in Browse's list on a phone. Tapping the row opens the book, and Own checks it off in place.
  let { book, line } = $props()

  const entry = $derived(entryFor(book.id))
  const rowPills = $derived(pills(progressPill(book, entry), wantPill(entry), availabilityPill(book), exclusivePill(book)))
</script>

<div class="brow">
  <span class="cover {lineClass(line)}" aria-hidden="true"></span>
  <span class="txt">
    <a class="name" href="/books/{book.id}">{book.name}</a>
    {#if rowPills.length > 0}
      <span class="tags">{#each rowPills as pill (pill.text)}<Pill {pill} />{/each}</span>
    {:else}
      <span class="subtle">Not on your list</span>
    {/if}
  </span>
  <OwnToggle {book} inList />
</div>
