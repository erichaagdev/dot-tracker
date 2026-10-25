<script>
  import { entryFor, rate } from '../lib/collection.svelte.js'

  // Picking the number that's already picked clears the rating
  let { book, which, label, hint } = $props()

  const scores = [0, 1, 2, 3, 4, 5]
  const id = $derived(`${book.id}-${which}`)
  const value = $derived(entryFor(book.id).ratings[which])
</script>

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
