# Dot Tracker

Dot Tracker is an app for you and your friends to keep track of your Lifelines FlowArt books. It has every book in one list, and each of you checks off the ones you own, marks the ones you want, follows your pages as you work through a book and rates it. You can all see how everyone else is doing, too. It works on a phone and on a computer, and it lives on the internet at its own address, so anyone you invite can use it from anywhere.

The first version starts from a spreadsheet of Lifelines books found online, which comes to 93 books across 16 product lines. You can fix anything in that list, like a page count, and add a new book yourself in a minute. Finding new books on the internet automatically comes later.

The mockups in `docs/mockups/index.html` show the main screens. Open the file in a browser, or on your phone, to see them at their real size. You can tap the page numbers, ratings and Own boxes to try them.

## What you can do in the first version

Browse shows every book in one running list, grouped by product line. You can search by name and narrow the list by product line or by what you own, want, are working on or have finished. Each book shows whether it's coming soon or no longer sold, and whether it's only sold at one store, like the Target exclusives. You can check off a book you own right from the list without opening it.

Opening a book shows everything about it and your copy of it on one screen. There's a box for owning it, and a "want it" choice of yes, maybe or no, just like the spreadsheet. Its progress is not started, in progress or finished.

Every page of the book shows as a numbered dot. Large Dot books have 25 pages and every other book has 20. Tap a dot when you finish that page, in any order you like. Finishing the first page moves the book to in progress, and finishing the last one marks it finished. You can also mark a book finished yourself without ticking every page.

Each book gets an overall rating from 0 to 5 for how much you liked it, and that's the rating your shelf and the lists show. If you want to say more, you can also rate the designs and the colors on their own. A rating says "not rated" until you pick a number, so a 0 means you really didn't like it. There's also a notes box for anything you want to remember, like which markers you swapped. Everyone in your group can read your notes, and a short line by the box says so.

Your shelf is the home screen. It shows the books you're working on with how far along you are, what you finished lately and how you rated it, and how much of the collection you have. The wishlist shows every book you marked as wanted and don't own yet.

To invite a friend, you type their email address and the app makes an invite link for it, which you text to them. They tap it and sign in with the Google account for that email address. The link only works with that account, so if it gets passed along, nobody else can join with it. Someone with more than one Google account gets asked which one to use, and if they pick the wrong one, the app tells them which address the invite is for so they can try again. Nobody has to make up or remember a password. Everyone starts with an empty shelf. A privacy page, linked from the sign-in screen, says what the app keeps about each person and who can see it.

## Seeing each other's books

Everyone in your group can see everyone else's books, with nothing hidden and nothing to set up. There are no friend requests, since everyone in the app was invited.

People is its own tab. It lists everyone in the group, you included, with how many books each person owns, is working on and has finished. Tap someone to see their shelf, laid out just like yours, with what they're working on and how far along they are, what they've finished and how they rated it, everything they own and their wishlist.

Open any book and, below your own copy, you'll see who else in the group is working on it or has finished it, whether they own it or want it, and the group's average rating. Tap a person to see their copy of that book, with their pages, dates, ratings and notes. You can look but not change anything, since only they can change their own copy.

You, and anyone you make an admin, look after the book list. When a new book comes out, a book stops being sold or a page count is wrong, you add or change it, and everyone sees the change.

Admins also look after the group on its own screen. It lists every invite that hasn't been used yet, with who it's for, which admin made it and when it runs out, so you can copy the link again or cancel it. Everyone in the group is listed there too, and you can make anyone an admin or a regular member. The app asks before it changes anyone's role, since a change signs that person out until they sign in again.

## The starting book list

The list comes from the spreadsheet found online, tidied up in a few ways.

- Three names are spelled right: Pumpkins & Pastures, Museum Masterpieces and Foliage and Flowers.
- Snowfall Serenity and Snowflake Serenity are one mini, called Snowflake Serenity like on the Lifelines website.
- Animals and Animals (Updated Version) are one Color to Reveal book, called Animals.
- Lovestruck/Made with Love is one book called Lovestruck, marked as no longer sold since it's gone from the website.
- Enchanted Forest has a price in the spreadsheet but no store, so it starts without a place to buy it until you add one.
- Corner Cafe, Cozy Companions and Cabin Getaway are Target exclusives. Other stores likely have exclusives too, and you can mark any book as one when you hear about it.
- The candle diffusers, journal set and detox kit from the Miscellaneous tab are left out, since they aren't books.

## What comes later

None of this is needed to start, and the order isn't set.

The app could find new books on its own. It would check the Lifelines website and store websites on a schedule, and maybe search the internet, for books that aren't in the list yet. New books would wait for you to approve them before they show up for everyone, so nothing odd slips in. The Lifelines Facebook page, where you hear about a lot of new books, is the hardest place for an app to read, so it's the least likely source.

Once the list grows, books could be grouped by the year they came out, like 2024 books and 2025 books.

Anyone without a Google account could sign in with Facebook instead.

Ideas for any time after that include adding a photo of each finished page, a count of books finished each month and a way to download your own list.

## How it gets built

Building happens in a fixed order. The starting book list comes first. Then come signing in and the book list, tracking your own copies with pages and ratings, the screens in the mockups, and the screens for adding books and inviting friends. Then the app goes online. You get the first invite link, and then you invite your friends. Last comes seeing each other's books, with People and the group's copies on every book. Keeping the app online on Google Cloud costs about $10 a month.
