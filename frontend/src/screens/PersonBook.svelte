<script>
  import ChevronLeft from '@lucide/svelte/icons/chevron-left'
  import { onMount } from 'svelte'
  import BookDetail from '../components/BookDetail.svelte'
  import Loading from '../components/Loading.svelte'
  import { findBook } from '../lib/catalog.svelte.js'
  import { untouched } from '../lib/collection.svelte.js'
  import { me } from '../lib/me.svelte.js'
  import { findPerson, loadPeople, loadShelf, people, shelves } from '../lib/people.svelte.js'
  import { cameFrom, goBack, navigate } from '../lib/router.svelte.js'

  // Someone's copy of a book, with their pages, progress, dates, ratings and notes, which only they can change. Your
  // own id opens your own copy instead.
  let { id, bookId } = $props()

  const yours = $derived(id === me.member?.id)
  const from = cameFrom()
  const book = $derived(findBook(bookId))
  const person = $derived(findPerson(id))
  const shelf = $derived(shelves[id])
  const entry = $derived(shelf?.data?.find((each) => each.bookId === bookId) ?? untouched(bookId))
  const backLabel = $derived(from === 'book' ? 'Book' : from === 'browse' ? 'Browse' : (person?.displayName ?? 'Back'))

  onMount(() => {
    if (yours) {
      navigate(`/books/${bookId}`, { replace: true })
      return
    }
    loadPeople()
    loadShelf(id)
  })

  function retry() {
    loadPeople()
    loadShelf(id)
  }
</script>

<div class="app-top">
  <button type="button" class="back" onclick={() => goBack(`/people/${id}`)}>
    <ChevronLeft class="ic" aria-hidden="true" />{backLabel}
  </button>
</div>

{#if yours}
  <Loading />
{:else if !book}
  <div class="screen-body missing">
    <h1 class="page-title">We couldn't find that book</h1>
    <p>It may have been taken off the list. Browse has every book there is.</p>
    <a class="btn" href="/browse">Go to Browse</a>
  </div>
{:else if shelf?.status === 'problem' || people.status === 'problem'}
  <Loading problem={shelf?.problem || people.problem} {retry} />
{:else if people.status === 'ready' && !person}
  <div class="screen-body missing">
    <h1 class="page-title">We couldn't find that person</h1>
    <p>They may have left the group. People has everyone who's in it now.</p>
    <a class="btn" href="/people">Go to People</a>
  </div>
{:else if shelf?.status !== 'ready' || !person}
  <Loading text="Getting their copy…" />
{:else}
  <div class="book-screen their-book">
    {#key book.id}<BookDetail {book} {person} {entry} />{/key}
    <p class="their-links"><a href="/books/{book.id}">Open your own copy of {book.name}</a></p>
  </div>
{/if}
