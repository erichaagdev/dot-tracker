<script>
  import ChevronRight from '@lucide/svelte/icons/chevron-right'
  import { onMount } from 'svelte'
  import AppTop from '../components/AppTop.svelte'
  import Loading from '../components/Loading.svelte'
  import Pill from '../components/Pill.svelte'
  import { initialsOf } from '../lib/format.js'
  import { me } from '../lib/me.svelte.js'
  import { loadPeople, people } from '../lib/people.svelte.js'
  import { countPills } from '../lib/status.js'

  // Everyone in the group with how their books are going. Each person opens their shelf, and you open your own.
  onMount(loadPeople)
</script>

<AppTop />
{#if people.status === 'problem'}
  <Loading problem={people.problem} retry={loadPeople} />
{:else if people.status !== 'ready'}
  <Loading text="Getting your group…" />
{:else}
  <div class="screen-body">
    <div>
      <h1 class="page-title">People</h1>
      <p class="subtle">Everyone in your group. Tap someone to see their shelf, their pages, ratings and notes.</p>
    </div>
    <div class="list-card people-list">
      {#each people.list as person (person.id)}
        {@const you = person.id === me.member?.id}
        <a class="list-row link person-row" href={you ? '/' : `/people/${person.id}`}>
          <span class="avatar" aria-hidden="true">{initialsOf(person.displayName)}</span>
          <span class="txt">
            <strong>{person.displayName}{#if you}<span class="you">You</span>{/if}</strong>
            <span class="tags">{#each countPills(person) as pill (pill.kind)}<Pill {pill} />{/each}</span>
          </span>
          <ChevronRight class="ic" aria-hidden="true" />
        </a>
      {/each}
    </div>
  </div>
{/if}
