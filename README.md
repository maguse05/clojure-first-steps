# Clojure Meetups

A website listing Clojure meetups around the world: a homepage with a map and list
of meetups, a page per meetup, and a footer linking to the community.

Built with [http-kit](https://github.com/http-kit/http-kit) and [Hiccup](https://github.com/weavejester/hiccup), pre-wired to deploy to Vercel.

## Where things live

| File | What it does |
| --- | --- |
| `resources/meetups.edn` | The meetup data. Add or edit meetups here. |
| `src/app/views.clj` | The HTML pages (homepage, meetup page, footer), written in Hiccup. |
| `src/app/core.clj` | The web server: routes, form handling. |
| `resources/public/style.css` | All the styling. |

> The newsletter sign-ups and "Add your meetup" submissions are only kept in memory
> (they're lost on restart). Connect them to a real service before going live.

## Why this exists

Clojure is a joy to write, but going from `hello world` to a URL you can share is where a lot of beginners get stuck.

This template exists to remove that friction. Click "Use this template", run a few commands, and you have a live production URL.
From there you can spend your time learning Clojure instead of fighting deployment.

## Quick start

1. Click **Use this template** above (or `git clone` this repo).
2. Run it locally:

   ```sh
   clojure -M:run
   ```

   Open http://localhost:8080 (set `PORT` to use something else, e.g. `PORT=3000 clojure -M:run`).
3. Ship it:

   ```sh
   npm i -g vercel
   vercel deploy
   ```
