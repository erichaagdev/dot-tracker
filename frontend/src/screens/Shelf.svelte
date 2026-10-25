<script>
  import AppTop from '../components/AppTop.svelte'
  import ShelfView from '../components/ShelfView.svelte'
  import { collection } from '../lib/collection.svelte.js'

  // The home screen: your own shelf
  const entries = $derived(Object.values(collection.entries))
</script>

<AppTop />
<div class="screen-body shelf">
  <ShelfView {entries} />
</div>
