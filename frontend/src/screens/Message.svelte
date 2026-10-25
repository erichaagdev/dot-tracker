<script>
  import AppTop from '../components/AppTop.svelte'

  // A screen with a short message and where to go from here, such as for a link that leads nowhere
  let { title, children } = $props()
</script>

<AppTop />
<div class="screen-body message-screen">
  <h1 class="page-title">{title}</h1>
  <p>{@render children()}</p>
</div>
