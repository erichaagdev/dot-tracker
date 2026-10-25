<script>
  let { pill } = $props()
</script>

<span class="pill {pill.kind}"><pill.icon class="ic sm" aria-hidden="true" />{pill.text}</span>
