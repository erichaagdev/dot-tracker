<script>
  import { entryFor, rate } from '../lib/collection.svelte.js'

  // Your own rating, where picking the number that's already picked clears it. Given someone else's entry, it shows
  // their rating as words and can't be changed.
  let { book, which, label, hint, entry = undefined } = $props()

  const scores = [0, 1, 2, 3, 4, 5]
  const readOnly = $derived(entry !== undefined)
  const id = $derived(`${book.id}-${which}`)
  const value = $derived((entry ?? entryFor(book.id)).ratings[which])
</script>

{#if readOnly}
  <div class="choice read-only">
    <h2 class="choice-label">{label}</h2>
    {#if value === null}
      <span class="score">Not rated</span>
    {:else}
      <span class="score">Rated <b>{value}</b> of 5</span>
    {/if}
  </div>
{:else}
  <div class="choice">
    <div class="line-h">
      <h2 class="choice-label" id={id}>{label}</h2>
      <small>{value === null ? 'Not rated yet' : hint}</small>
    </div>
    <div class="seg" role="group" aria-labelledby={id}>
      {#each scores as score (score)}
        <button type="button" class:on={value === score} aria-pressed={value === score}
          onclick={() => rate(book, which, value === score ? null : score)}>{score}</button>
      {/each}
    </div>
  </div>
{/if}
