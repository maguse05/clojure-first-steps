# vercel-clojure

A minimal [http-kit](https://github.com/http-kit/http-kit) web app, pre-wired to deploy to Vercel.

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
