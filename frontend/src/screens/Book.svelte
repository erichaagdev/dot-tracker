<script>
  import ChevronLeft from '@lucide/svelte/icons/chevron-left'
  import BookDetail from '../components/BookDetail.svelte'
  import { findBook } from '../lib/catalog.svelte.js'
  import { cameFrom, goBack } from '../lib/router.svelte.js'

  // One book as its own screen on a phone. From 900 px, Browse shows the same content beside the list instead.
  let { id } = $props()

  const backLabels = { shelf: 'Shelf', browse: 'Browse', wishlist: 'Wishlist' }
  const from = cameFrom()
  const backLabel = from ? (backLabels[from] ?? 'Back') : 'Browse'

  const book = $derived(findBook(id))
</script>

<div class="app-top">
  <button type="button" class="back" onclick={() => goBack('/browse')}>
    <ChevronLeft class="ic" aria-hidden="true" />{backLabel}
  </button>
</div>

{#if book}
  <div class="book-screen">
    {#key book.id}<BookDetail {book} />{/key}
  </div>
{:else}
  <div class="screen-body missing">
    <h1 class="page-title">We couldn't find that book</h1>
    <p>It may have been taken off the list. Browse has every book there is.</p>
    <a class="btn" href="/browse">Go to Browse</a>
  </div>
{/if}
