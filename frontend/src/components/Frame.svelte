<script>
  import Library from '@lucide/svelte/icons/library'
  import Heart from '@lucide/svelte/icons/heart'
  import Plus from '@lucide/svelte/icons/plus'
  import Search from '@lucide/svelte/icons/search'
  import User from '@lucide/svelte/icons/user'
  import Users from '@lucide/svelte/icons/users'
  import Brand from './Brand.svelte'
  import { initials, isAdmin } from '../lib/me.svelte.js'
  import { route } from '../lib/router.svelte.js'

  let { children } = $props()

  const tabs = [
    { href: '/', label: 'Shelf', icon: Library, routes: ['shelf'] },
    { href: '/browse', label: 'Browse', icon: Search, routes: ['browse', 'book'] },
    { href: '/wishlist', label: 'Wishlist', icon: Heart, routes: ['wishlist'] },
  ]

  const on = (routes) => routes.includes(route.name)
</script>

<div class="app">
  <header class="topbar">
    <Brand href="/" />
    <nav class="nav-list" aria-label="Main">
      {#each tabs as tab (tab.href)}
        <a class="nav" class:on={on(tab.routes)} href={tab.href} aria-current={on(tab.routes) ? 'page' : undefined}>
          <tab.icon class="ic" aria-hidden="true" />{tab.label}
        </a>
      {/each}
    </nav>
    <span class="end">
      {#if isAdmin()}
        <a class="nav" class:on={on(['new-book'])} href="/books/new"><Plus class="ic" aria-hidden="true" />Add a book</a>
        <a class="nav" class:on={on(['group'])} href="/group"><Users class="ic" aria-hidden="true" />Group</a>
        <span class="sep"></span>
      {/if}
      <a class="nav" class:on={on(['account'])} href="/account" aria-current={on(['account']) ? 'page' : undefined}>
        <span class="avatar" aria-hidden="true">{initials()}</span>Account
      </a>
    </span>
  </header>

  <main class="main">
    {@render children()}
  </main>

  <!-- A phone reaches Group from Account, so the Account tab stays lit there -->
  <nav class="tabbar" aria-label="Main">
    {#each [...tabs, { href: '/account', label: 'Account', icon: User, routes: ['account', 'group'] }] as tab (tab.href)}
      <a class="tab" class:on={on(tab.routes)} href={tab.href} aria-current={on(tab.routes) ? 'page' : undefined}>
        <tab.icon class="ic" aria-hidden="true" />{tab.label}
      </a>
    {/each}
  </nav>
</div>
