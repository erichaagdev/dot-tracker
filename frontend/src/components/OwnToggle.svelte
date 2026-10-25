<script>
  import Check from '@lucide/svelte/icons/check'
  import { changeOwned, entryFor } from '../lib/collection.svelte.js'

  // In a list the label is "Own", and the book's name is added for screen readers
  let { book, label = 'Own', inList = false } = $props()

  const owned = $derived(entryFor(book.id).owned)
</script>

<button type="button" class="own" class:on={owned} aria-pressed={owned} onclick={() => changeOwned(book, !owned)}>
  <span class="box"><Check class="ic" aria-hidden="true" /></span>{label}{#if inList}<span class="visually-hidden">, {book.name}</span>{/if}
</button>
