<script>
  import Check from '@lucide/svelte/icons/check'
  import ChevronDown from '@lucide/svelte/icons/chevron-down'
  import ChevronLeft from '@lucide/svelte/icons/chevron-left'
  import CircleAlert from '@lucide/svelte/icons/circle-alert'
  import Plus from '@lucide/svelte/icons/plus'
  import Trash from '@lucide/svelte/icons/trash-2'
  import X from '@lucide/svelte/icons/x'
  import { tick } from 'svelte'
  import { addBook, catalog, deleteBook, findBook, findLine, retailers, updateBook } from '../lib/catalog.svelte.js'
  import { refreshCollection } from '../lib/collection.svelte.js'
  import { cameFrom, goBack, navigate } from '../lib/router.svelte.js'

  // Adds a book, or edits the one with this id. The parent keys it by id, so the form starts over for each book.
  let { id = undefined } = $props()

  const availabilities = [
    { value: 'AVAILABLE', label: 'Available' },
    { value: 'COMING_SOON', label: 'Coming soon' },
    { value: 'RETIRED', label: 'Retired' },
    { value: 'UNKNOWN', label: 'Not sure' },
  ]
  const backLabels = { shelf: 'Shelf', browse: 'Browse', wishlist: 'Wishlist', account: 'Account' }
  const from = cameFrom()

  let nextKey = 0
  let form = $state(startingForm())
  // A new book's pages follow its line's default until someone types a count of their own
  let pagesTyped = $state(false)
  let saving = $state(false)
  let problem = $state('')
  let confirming = $state(false)
  let deleting = $state(false)
  let deleteProblem = $state('')

  const book = $derived(id ? findBook(id) : undefined)
  const line = $derived(findLine(form.lineId))
  const stores = $derived(retailers())
  const backLabel = $derived(from && from !== 'book' ? (backLabels[from] ?? 'Back') : (book?.name ?? 'Browse'))
  const fallback = $derived(book ? `/books/${book.id}` : '/browse')

  function startingForm() {
    const start = id ? findBook(id) : undefined
    const firstLine = catalog.lines[0]
    return {
      name: start?.name ?? '',
      lineId: start?.productLineId ?? firstLine?.id ?? '',
      pages: String(start?.pageCount ?? firstLine?.defaultPageCount ?? ''),
      availability: start?.availability ?? 'AVAILABLE',
      listings: (start?.listings ?? []).map((listing) => ({
        key: nextKey++,
        retailer: listing.retailer,
        price: listing.priceCents === null ? '' : (listing.priceCents / 100).toFixed(2),
        url: listing.url ?? '',
        exclusive: listing.exclusive,
      })),
    }
  }

  function pickLine(lineId) {
    form.lineId = lineId
    if (!book && !pagesTyped) {
      form.pages = String(findLine(lineId)?.defaultPageCount ?? form.pages)
    }
  }

  async function addStore() {
    const key = nextKey++
    form.listings.push({ key, retailer: '', price: '', url: '', exclusive: false })
    await tick()
    document.getElementById(`store-${key}`)?.focus()
  }

  function removeStore(key) {
    form.listings = form.listings.filter((listing) => listing.key !== key)
  }

  // The book as the backend takes it, or a plain sentence about what to fix first. A store left completely blank is
  // dropped rather than refused.
  function check() {
    const name = form.name.trim()
    if (!name) {
      return { problem: 'Give the book a name.' }
    }
    if (!line) {
      return { problem: 'Pick the product line the book belongs to.' }
    }
    const pages = form.pages.trim()
    if (!/^\d+$/.test(pages) || Number(pages) < 1) {
      return { problem: 'Pages needs a whole number, 1 or more.' }
    }
    const listings = []
    for (const draft of form.listings) {
      const retailer = draft.retailer.trim()
      const price = draft.price.trim()
      const url = draft.url.trim()
      if (!retailer && !price && !url) {
        continue
      }
      if (!retailer) {
        return { problem: "Every store needs a name. Remove the ones you don't need." }
      }
      const priceCents = cents(price)
      if (priceCents === undefined) {
        return { problem: `Write the price at ${retailer} in dollars, like 14.99.` }
      }
      listings.push({ retailer, priceCents, url: url ? withScheme(url) : null, exclusive: draft.exclusive })
    }
    return {
      details: { productLineId: line.id, name, availability: form.availability, pageCount: Number(pages), listings },
    }
  }

  /** A price in dollars, such as 14.99 or $15, in cents, with null for no price and undefined for one that isn't. */
  function cents(text) {
    if (!text) {
      return null
    }
    const plain = text.replace(/^\$\s*/, '').replaceAll(',', '')
    return /^(\d+(\.\d{0,2})?|\.\d{1,2})$/.test(plain) ? Math.round(Number(plain) * 100) : undefined
  }

  function withScheme(url) {
    return /^https?:\/\//i.test(url) ? url : `https://${url}`
  }

  async function save(event) {
    event.preventDefault()
    const checked = check()
    problem = checked.problem ?? ''
    if (checked.problem) {
      return
    }
    saving = true
    try {
      if (book) {
        const pageCountChanged = checked.details.pageCount !== book.pageCount
        const bookId = book.id
        await updateBook(bookId, checked.details)
        if (pageCountChanged) {
          refreshCollection()
        }
        goBack(`/books/${bookId}`)
      } else {
        const added = await addBook(checked.details)
        navigate(`/books/${added.id}`, { replace: true })
      }
    } catch (error) {
      if (error.status !== 401) {
        problem = error.message
      }
    } finally {
      saving = false
    }
  }

  function askToDelete() {
    deleteProblem = ''
    confirming = true
  }

  async function remove() {
    deleting = true
    try {
      await deleteBook(book.id)
      navigate('/browse', { replace: true })
    } catch (error) {
      confirming = false
      if (error.status !== 401) {
        deleteProblem = error.message
      }
    } finally {
      deleting = false
    }
  }
</script>

<div class="app-top">
  <button type="button" class="back" onclick={() => goBack(fallback)}>
    <ChevronLeft class="ic" aria-hidden="true" />{backLabel}
  </button>
</div>

{#if id && !book}
  <div class="screen-body missing">
    <h1 class="page-title">We couldn't find that book</h1>
    <p>It may have been taken off the list. Browse has every book there is.</p>
    <a class="btn" href="/browse">Go to Browse</a>
  </div>
{:else}
  <form class="screen-body editor" onsubmit={save} novalidate>
    <h1 class="page-title">{book ? 'Edit book' : 'Add a book'}</h1>

    <div class="field">
      <label class="field-label" for="book-name">Name</label>
      <input class="input" id="book-name" bind:value={form.name} autocomplete="off" autocapitalize="words" />
    </div>

    <div class="field-row">
      <div class="field grow">
        <label class="field-label" for="book-line">Product line</label>
        <span class="select-box">
          <select class="input" id="book-line" value={form.lineId} onchange={(event) => pickLine(event.currentTarget.value)}>
            {#each catalog.lines as each (each.id)}<option value={each.id}>{each.name}</option>{/each}
          </select>
          <ChevronDown class="ic sm" aria-hidden="true" />
        </span>
      </div>
      <div class="field pages-field">
        <label class="field-label" for="book-pages">Pages</label>
        <input class="input" id="book-pages" inputmode="numeric" maxlength="3" autocomplete="off" bind:value={form.pages}
          oninput={() => (pagesTyped = true)} />
      </div>
    </div>

    <div class="field">
      <span class="field-label" id="book-availability">Can you buy it?</span>
      <div class="seg availability" role="group" aria-labelledby="book-availability">
        {#each availabilities as choice (choice.value)}
          <button type="button" class:on={form.availability === choice.value} aria-pressed={form.availability === choice.value}
            onclick={() => (form.availability = choice.value)}>{choice.label}</button>
        {/each}
      </div>
    </div>

    <div class="field">
      <span class="field-label">Where to buy</span>
      {#each form.listings as listing, i (listing.key)}
        <div class="card store-card" role="group" aria-label="Store {i + 1}">
          <div class="field">
            <label class="field-label" for="store-{listing.key}">Store</label>
            <input class="input" id="store-{listing.key}" list="retailers" autocomplete="off" bind:value={listing.retailer} />
          </div>
          <div class="field-row">
            <div class="field price-field">
              <label class="field-label" for="price-{listing.key}">Price</label>
              <span class="money">
                <span aria-hidden="true">$</span>
                <input class="input" id="price-{listing.key}" inputmode="decimal" autocomplete="off" placeholder="14.99"
                  bind:value={listing.price} />
              </span>
            </div>
            <div class="field grow">
              <label class="field-label" for="url-{listing.key}">Link <small>optional</small></label>
              <input class="input" id="url-{listing.key}" type="url" inputmode="url" autocomplete="off" autocapitalize="off"
                spellcheck="false" placeholder="https://" bind:value={listing.url} />
            </div>
          </div>
          <div class="store-actions">
            <button type="button" class="own" class:on={listing.exclusive} aria-pressed={listing.exclusive}
              onclick={() => (listing.exclusive = !listing.exclusive)}>
              <span class="box"><Check class="ic" aria-hidden="true" /></span>Only sold here
            </button>
            <button type="button" class="btn ghost danger" onclick={() => removeStore(listing.key)}>
              <X class="ic" aria-hidden="true" />Remove<span class="visually-hidden"> {listing.retailer || 'this store'}</span>
            </button>
          </div>
        </div>
      {:else}
        <p class="subtle">No stores yet. Add one so everyone knows where to find it.</p>
      {/each}
      <button type="button" class="btn ghost" onclick={addStore}><Plus class="ic" aria-hidden="true" />Add a store</button>
      <datalist id="retailers">
        {#each stores as store (store)}<option value={store}></option>{/each}
      </datalist>
    </div>

    {#if book}
      <section class="block delete-book" aria-labelledby="delete-heading">
        <h2 class="section-title" id="delete-heading">Delete this book</h2>
        <p class="subtle">Only for a book added by mistake. A book someone in the group tracks can't be deleted, so mark it Retired instead.</p>
        {#if confirming}
          <div class="card confirm">
            <p>Delete {book.name} for everyone? This can't be undone.</p>
            <div class="confirm-actions">
              <button type="button" class="btn danger" onclick={remove} disabled={deleting}>
                <Trash class="ic" aria-hidden="true" />{deleting ? 'Deleting…' : 'Delete it'}
              </button>
              <button type="button" class="btn ghost" onclick={() => (confirming = false)}>Keep it</button>
            </div>
          </div>
        {:else}
          <div>
            <button type="button" class="btn ghost danger" onclick={askToDelete}><Trash class="ic" aria-hidden="true" />Delete this book</button>
          </div>
        {/if}
        <div role="alert">
          {#if deleteProblem}
            <div class="message-box"><CircleAlert class="ic" aria-hidden="true" /><p>{deleteProblem}</p></div>
          {/if}
        </div>
      </section>
    {/if}

    <div class="actionbar">
      <div role="alert">
        {#if problem}
          <div class="message-box"><CircleAlert class="ic" aria-hidden="true" /><p>{problem}</p></div>
        {/if}
      </div>
      <div class="actionbar-buttons">
        <button type="button" class="btn ghost cancel" onclick={() => goBack(fallback)}>Cancel</button>
        <button type="submit" class="btn" disabled={saving}>{saving ? 'Saving…' : book ? 'Save changes' : 'Add this book'}</button>
      </div>
    </div>
  </form>
{/if}
