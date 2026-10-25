<script>
  import ChevronRight from '@lucide/svelte/icons/chevron-right'
  import { onMount } from 'svelte'
  import Pill from './Pill.svelte'
  import { entryFor, settled } from '../lib/collection.svelte.js'
  import { initialsOf } from '../lib/format.js'
  import { me } from '../lib/me.svelte.js'
  import { bookEntries, isTracked, loadBookEntries } from '../lib/people.svelte.js'
  import { pills, theirOwnPill, theirProgressPill } from '../lib/status.js'

  // The rest of the group's copies of a book, below your own, with the group's average rating. The parent keys it by
  // book, so it fetches a fresh copy for each book it opens.
  let { book } = $props()

  const order = { IN_PROGRESS: 0, FINISHED: 1, NOT_STARTED: 2 }

  const loaded = $derived(bookEntries[book.id])
  const others = $derived((loaded?.data?.entries ?? [])
    .filter((each) => each.memberId !== me.member?.id && isTracked(each.entry))
    .toSorted((a, b) => order[a.entry.progress] - order[b.entry.progress]))
  const working = $derived(others.filter((each) => each.entry.progress === 'IN_PROGRESS'))
  const finished = $derived(others.filter((each) => each.entry.progress === 'FINISHED'))
  const average = $derived(loaded?.data?.averageRating ?? null)
  const ratingCount = $derived(loaded?.data?.ratingCount ?? 0)

  onMount(() => loadBookEntries(book.id))

  // Your own rating counts toward the group's average, so once a change to it is saved the group is fetched again.
  // A rating is a number or null, so undefined means the effect hasn't run yet.
  let lastRating
  $effect(() => {
    const overall = entryFor(book.id).ratings.overall
    if (lastRating !== undefined && overall !== lastRating) {
      settled(book.id).then(() => loadBookEntries(book.id))
    }
    lastRating = overall
  })

  function names(list) {
    const all = list.map((each) => each.displayName)
    return all.length > 1 ? `${all.slice(0, -1).join(', ')} and ${all.at(-1)}` : all[0]
  }

  function summary() {
    const parts = []
    if (working.length > 0) {
      parts.push(`${names(working)} ${working.length === 1 ? 'is' : 'are'} working on it`)
    }
    if (finished.length > 0) {
      parts.push(`${names(finished)} finished it`)
    }
    return parts.length > 0 ? `${parts.join(', and ')}.` : 'Nobody else has started it yet.'
  }

  function people(count) {
    return count === 1 ? '1 person' : `${count} people`
  }
</script>

<section class="block" aria-labelledby="{book.id}-group">
  <h2 class="section-title" id="{book.id}-group">Your group</h2>
  {#if loaded?.status === 'problem'}
    <div class="card empty-card">
      <p>{loaded.problem}</p>
      <button type="button" class="btn ghost" onclick={() => loadBookEntries(book.id)}>Try again</button>
    </div>
  {:else if loaded?.status !== 'ready'}
    <p class="subtle" role="status">Getting your group's copies…</p>
  {:else if others.length === 0}
    <div class="card empty-card">
      <p>Nobody else in your group has this book on their list yet. When someone does, you'll see how they're doing here.</p>
    </div>
  {:else}
    <div class="stats">
      <div class="stat"><b>{working.length}</b><span>working on it</span></div>
      <div class="stat"><b>{finished.length}</b><span>finished it</span></div>
      {#if average === null}
        <div class="stat"><b>0</b><span>ratings yet</span></div>
      {:else}
        <div class="stat"><b>{Number(average.toFixed(1))}</b><span>group rating, from {people(ratingCount)}</span></div>
      {/if}
    </div>
    <p class="subtle their-summary">{summary()}</p>
    <div class="list-card people-list">
      {#each others as person (person.memberId)}
        {@const personPills = pills(theirProgressPill(book, person.entry), theirOwnPill(person.entry))}
        <a class="list-row link person-row" href="/people/{person.memberId}/books/{book.id}">
          <span class="avatar" aria-hidden="true">{initialsOf(person.displayName)}</span>
          <span class="txt">
            <strong>{person.displayName}</strong>
            <span class="tags">
              {#each personPills as pill (pill.text)}<Pill {pill} />{/each}
              {#if person.entry.ratings.overall !== null}<span class="score">Rated <b>{person.entry.ratings.overall}</b></span>{/if}
            </span>
          </span>
          <ChevronRight class="ic" aria-hidden="true" />
        </a>
      {/each}
    </div>
  {/if}
</section>
