<script>
  import { untrack } from 'svelte'
  import Frame from './components/Frame.svelte'
  import Loading from './components/Loading.svelte'
  import Notice from './components/Notice.svelte'
  import Ready from './components/Ready.svelte'
  import Account from './screens/Account.svelte'
  import Book from './screens/Book.svelte'
  import BookEditor from './screens/BookEditor.svelte'
  import Browse from './screens/Browse.svelte'
  import Group from './screens/Group.svelte'
  import Message from './screens/Message.svelte'
  import People from './screens/People.svelte'
  import Person from './screens/Person.svelte'
  import PersonBook from './screens/PersonBook.svelte'
  import Privacy from './screens/Privacy.svelte'
  import Shelf from './screens/Shelf.svelte'
  import SignIn from './screens/SignIn.svelte'
  import Welcome from './screens/Welcome.svelte'
  import Wishlist from './screens/Wishlist.svelte'
  import { catalog, loadCatalog } from './lib/catalog.svelte.js'
  import { collection, loadCollection } from './lib/collection.svelte.js'
  import { isAdmin, loadMe, me } from './lib/me.svelte.js'
  import { followLink, route } from './lib/router.svelte.js'
  import { viewport } from './lib/viewport.svelte.js'

  // The welcome screen is for someone who hasn't signed in yet and the privacy page is for anyone, so neither asks
  // who's signed in
  $effect(() => {
    if (route.name !== 'welcome' && route.name !== 'privacy' && me.status === 'idle') {
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

{#if route.name === 'privacy'}
  <Privacy />
{:else if route.name === 'welcome' && me.status !== 'signed-in'}
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
      <Ready><Shelf /></Ready>
    {:else if route.name === 'people'}
      <Ready><People /></Ready>
    {:else if route.name === 'person'}
      <Ready>{#key route.params.id}<Person id={route.params.id} />{/key}</Ready>
    {:else if route.name === 'person-book'}
      <Ready>{#key `${route.params.id}/${route.params.bookId}`}<PersonBook id={route.params.id} bookId={route.params.bookId} />{/key}</Ready>
    {:else if route.name === 'wishlist'}
      <Ready><Wishlist /></Ready>
    {:else if route.name === 'account'}
      <Account />
    {:else if (route.name === 'new-book' || route.name === 'edit-book') && !isAdmin()}
      <Message title="Only admins can change books">An admin in your group looks after the list of books. If something needs adding or fixing, let one of them know. <a href="/browse">Browse</a> has every book there is.</Message>
    {:else if route.name === 'new-book'}
      <Ready><BookEditor /></Ready>
    {:else if route.name === 'edit-book'}
      <Ready>{#key route.params.id}<BookEditor id={route.params.id} />{/key}</Ready>
    {:else if route.name === 'group' && !isAdmin()}
      <Message title="Only admins look after the group">To invite a friend, ask an admin in your group for an invite link. Your <a href="/">shelf</a> is right where you left it.</Message>
    {:else if route.name === 'group'}
      <Group />
    {:else if route.name === 'welcome'}
      <Message title="You're already in">You're signed in already, so there's nothing more to do with this invite. Your <a href="/">shelf</a> is ready.</Message>
    {:else}
      <Message title="We couldn't find that page">The link may be old or mistyped. Your <a href="/">shelf</a> is a good place to start.</Message>
    {/if}
  </Frame>
{/if}

<Notice />
