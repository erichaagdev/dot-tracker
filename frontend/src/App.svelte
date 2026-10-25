<script>
  import { untrack } from 'svelte'
  import Frame from './components/Frame.svelte'
  import Loading from './components/Loading.svelte'
  import Notice from './components/Notice.svelte'
  import Ready from './components/Ready.svelte'
  import Account from './screens/Account.svelte'
  import Book from './screens/Book.svelte'
  import Browse from './screens/Browse.svelte'
  import Placeholder from './screens/Placeholder.svelte'
  import SignIn from './screens/SignIn.svelte'
  import Welcome from './screens/Welcome.svelte'
  import { catalog, loadCatalog } from './lib/catalog.svelte.js'
  import { collection, loadCollection } from './lib/collection.svelte.js'
  import { loadMe, me } from './lib/me.svelte.js'
  import { followLink, route } from './lib/router.svelte.js'
  import { viewport } from './lib/viewport.svelte.js'

  // The welcome screen is for someone who hasn't signed in yet, so it never asks who's signed in
  $effect(() => {
    if (route.name !== 'welcome' && me.status === 'idle') {
      untrack(loadMe)
    }
  })

  $effect(() => {
    if (me.status === 'signed-in') {
      untrack(() => {
        if (catalog.status === 'idle') {
          loadCatalog()
        }
        if (collection.status === 'idle') {
          loadCollection()
        }
      })
    }
  })
</script>

<svelte:document onclick={followLink} />

{#if route.name === 'welcome' && me.status !== 'signed-in'}
  <Welcome />
{:else if me.status === 'signed-out'}
  <SignIn />
{:else if me.status === 'problem'}
  <Loading problem={me.problem} retry={loadMe} />
{:else if me.status !== 'signed-in'}
  <Loading text="Opening Dot Tracker…" />
{:else}
  <Frame>
    {#if route.name === 'browse' || (route.name === 'book' && viewport.wide)}
      <Ready><Browse selectedId={route.params.id} /></Ready>
    {:else if route.name === 'book'}
      <Ready><Book id={route.params.id} /></Ready>
    {:else if route.name === 'shelf'}
      <Placeholder title="Your shelf">Your shelf is on its way. Until then, <a href="/browse">Browse</a> has every book.</Placeholder>
    {:else if route.name === 'wishlist'}
      <Placeholder title="Your wishlist">Your wishlist is on its way. Until then, the Want filter in <a href="/browse">Browse</a> shows the books you want.</Placeholder>
    {:else if route.name === 'account'}
      <Account />
    {:else if route.name === 'new-book'}
      <Placeholder title="Add a book">Adding books is on its way.</Placeholder>
    {:else if route.name === 'group'}
      <Placeholder title="Your group">Inviting friends and looking after the group is on its way.</Placeholder>
    {:else if route.name === 'welcome'}
      <Placeholder title="You're already in">You're signed in already, so there's nothing more to do with this invite. Your <a href="/">shelf</a> is ready.</Placeholder>
    {:else}
      <Placeholder title="We couldn't find that page">The link may be old or mistyped. Your <a href="/">shelf</a> is a good place to start.</Placeholder>
    {/if}
  </Frame>
{/if}

<Notice />
