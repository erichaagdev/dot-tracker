<script>
  import Loading from './Loading.svelte'
  import { catalog, loadCatalog } from '../lib/catalog.svelte.js'
  import { collection, loadCollection } from '../lib/collection.svelte.js'

  // Shows its screen once the catalog and the member's collection have loaded
  let { children } = $props()

  const problem = $derived(catalog.problem || collection.problem)
  const failed = $derived(catalog.status === 'problem' || collection.status === 'problem')

  function retry() {
    if (catalog.status === 'problem') {
      loadCatalog()
    }
    if (collection.status === 'problem') {
      loadCollection()
    }
  }
</script>

{#if failed}
  <Loading {problem} {retry} />
{:else if catalog.status === 'ready' && collection.status === 'ready'}
  {@render children()}
{:else}
  <Loading />
{/if}
