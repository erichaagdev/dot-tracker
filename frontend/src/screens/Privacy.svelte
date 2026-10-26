<script>
  import Brand from '../components/Brand.svelte'

  // Open to anyone, signed in or not, since Google's sign-in setup links here as the app's privacy policy
</script>

<div class="welcome">
  <article class="welcome-card welcome-doc">
    <Brand large href="/" />
    <h1>Privacy</h1>
    <p class="lead">Dot Tracker is a small app that a group of friends uses to keep track of their Lifelines FlowArt books. It's a hobby project, not a business, and it has no ads. This page says what it keeps about you and who can see it.</p>

    <h2>What it keeps</h2>
    <p>When you sign in with Google, Dot Tracker gets your name, your email address and your Google account's id, and nothing else from your Google account. It never sees your contacts, mail, files or calendar, and it never posts anything for you.</p>
    <p>It also keeps what you put into it, which is the books you own and want, how far along you are and which pages you've finished, your ratings and notes, and the dates you started and finished each book. It notes when you joined, who invited you and whether you're an admin.</p>
    <p>Before you join, it keeps the email address an admin entered when they invited you, so that only the Google account for that address can use the invite. The address stays with the invite after you join or the invite expires.</p>

    <h2>Who can see it</h2>
    <p>Everyone in the group can see your name and everything about your books, including your notes and your wishlist. Admins can also see your email address, and the address each open invite was sent to, so they can tell people apart when they look after the group. Nobody outside the group can see any of it.</p>

    <h2>What it never does</h2>
    <p>Dot Tracker never sells or shares your information, and it has no tracking or analytics. Your Google account is used only to sign you in and to show your name to the group.</p>

    <h2>Cookies and your browser</h2>
    <p>Dot Tracker sets two cookies. One keeps you signed in for up to 90 days, and the other protects your changes from forged requests. Your browser also remembers your last search and filters on Browse, on that device only.</p>

    <h2>Where it's stored</h2>
    <p>Everything is stored on Google Cloud in the United States. The database is backed up every day, and each backup is kept for 7 days. Google Cloud also keeps a log of the requests made to the app, with the time and the IP address each came from, for 30 days, to help find and fix problems.</p>

    <h2>Leaving the group</h2>
    <p>To leave the group and have your account and everything in it deleted, ask an admin. Once it's deleted, it's gone from the backups within 7 days too.</p>

    <h2>Questions</h2>
    <p>Ask the person who invited you, or any admin in the group.</p>

    <a class="btn ghost" href="/">Go to Dot Tracker</a>
  </article>
</div>
