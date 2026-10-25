<script>
  import AppTop from '../components/AppTop.svelte'

  // A screen that's still on its way, with a line about where to go meanwhile
  let { title, children } = $props()
</script>

<AppTop />
<div class="screen-body placeholder">
  <h1 class="page-title">{title}</h1>
  <p>{@render children()}</p>
</div>
