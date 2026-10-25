# Dot Tracker

Dot Tracker is an app for you and your friends to keep track of your Lifelines FlowArt books. It has every book in one list, and each of you checks off the ones you own, marks the ones you want, follows your pages as you work through a book and rates it. It works on a phone and on a computer, and it lives on the internet at its own address, so anyone you invite can use it from anywhere.

The first version starts from a spreadsheet of Lifelines books found online, which comes to 93 books across 16 product lines. You can fix anything in that list, like a page count, and add a new book yourself in a minute. Finding new books on the internet automatically comes later.

The mockups in `docs/mockups/index.html` show every screen. Open the file in a browser, or on your phone, to see them at their real size. You can tap the page numbers, ratings and Own boxes to try them.

## What you can do in the first version

Browse shows every book in one running list, grouped by product line. You can search by name and narrow the list by product line or by what you own, want, are working on or have finished. Each book shows whether it's coming soon or no longer sold, and whether it's only sold at one store, like the Target exclusives. You can check off a book you own right from the list without opening it.

Opening a book shows everything about it and your copy of it on one screen. There's a box for owning it, and a "want it" choice of yes, maybe or no, just like the spreadsheet. Its progress is not started, in progress or finished.

Every page of the book shows as a numbered dot. Large Dot books have 25 pages and every other book has 20. Tap a dot when you finish that page, in any order you like. Finishing the first page moves the book to in progress, and finishing the last one marks it finished. You can also mark a book finished yourself without ticking every page.

Each book gets an overall rating from 0 to 5 for how much you liked it, and that's the rating your shelf and the lists show. If you want to say more, you can also rate the designs and the colors on their own. A rating says "not rated" until you pick a number, so a 0 means you really didn't like it. There's also a notes box for anything you want to remember, like which markers you swapped, and only you see it.

Your shelf is the home screen. It shows the books you're working on with how far along you are, what you finished lately and how you rated it, and how much of the collection you have. The wishlist shows every book you marked as wanted and don't own yet.

To join, a friend gets an invite link from you by text, taps it and signs in with their Google account. Nobody has to make up or remember a password. Everyone starts with an empty shelf and sees only their own checkmarks, pages and ratings.

You, and anyone you make an admin, look after the book list. When a new book comes out, a book stops being sold or a page count is wrong, you add or change it, and everyone sees the change.

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

Everyone could see each other's books. You'd see each person's shelf, what they're working on and how they rated each book, and every book would show the group's average rating and who owns it. There wouldn't be friend requests or profiles, since everyone in the app was invited. Each person would have checkboxes for whether others see their ratings and their pages, and each book would have a private note just for you and a public note others can read.

Once the list grows, books could be grouped by the year they came out, like 2024 books and 2025 books.

Anyone without a Google account could sign in with Facebook instead.

Ideas for any time after that include adding a photo of each finished page, a count of books finished each month and a way to download your own list.

## How it gets built

Building happens in a fixed order. The starting book list comes first. Then come signing in and the book list, tracking your own copies with pages and ratings, the screens in the mockups, and the screens for adding books and inviting friends. Last, the app goes online. You get the first invite link, and then you invite your friends. Keeping the app online on Google Cloud costs about $10 a month.
