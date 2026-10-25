<script>
  import ChevronLeft from '@lucide/svelte/icons/chevron-left'
  import { onMount } from 'svelte'
  import Loading from '../components/Loading.svelte'
  import ShelfView from '../components/ShelfView.svelte'
  import { me } from '../lib/me.svelte.js'
  import { findPerson, loadPeople, loadShelf, people, shelves } from '../lib/people.svelte.js'
  import { goBack, navigate } from '../lib/router.svelte.js'

  // Someone's shelf, laid out like your own. Your own id opens your own shelf instead.
  let { id } = $props()

  const yours = $derived(id === me.member?.id)
  const person = $derived(findPerson(id))
  const shelf = $derived(shelves[id])

  onMount(() => {
    if (yours) {
      navigate('/', { replace: true })
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
  <button type="button" class="back" onclick={() => goBack('/people')}>
    <ChevronLeft class="ic" aria-hidden="true" />People
  </button>
</div>

{#if yours}
  <Loading />
{:else if shelf?.status === 'problem' || people.status === 'problem'}
  <Loading problem={shelf?.problem || people.problem} {retry} />
{:else if people.status === 'ready' && !person}
  <div class="screen-body missing">
    <h1 class="page-title">We couldn't find that person</h1>
    <p>They may have left the group. People has everyone who's in it now.</p>
    <a class="btn" href="/people">Go to People</a>
  </div>
{:else if shelf?.status !== 'ready' || !person}
  <Loading text="Getting their books…" />
{:else}
  <div class="screen-body shelf">
    <ShelfView entries={shelf.data} {person} />
  </div>
{/if}
