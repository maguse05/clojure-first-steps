# Clojure Meetups

Map and directory of Clojure meetups, plus the application form for the
Clojure Meetup Pilot Program.

Ring app on [http-kit](https://github.com/http-kit/http-kit), routed with
[reitit](https://github.com/metosin/reitit) and rendered with
[Hiccup](https://github.com/weavejester/hiccup). The map uses
[Leaflet](https://leafletjs.com) and OpenStreetMap tiles.

## Running it

```sh
clojure -M:run
```

Then open http://localhost:8080 (`PORT=3000 clojure -M:run` for another port).

From a REPL:

```clojure
(require 'app.core)
(app.core/start! {:port 8080})
(app.core/stop!)
```

## Layout

| Path | |
| --- | --- |
| `resources/meetups.edn` | Meetup data. Re-read on every request, no restart needed. |
| `src/app/routes.clj` | Routes and handlers. |
| `src/app/application.clj` | Application form fields and validation, as data. |
| `src/app/countries.clj` | Country names and matching for the country filter. |
| `src/app/views/` | Hiccup views: layout, home, meetup page, map, form. |
| `resources/public/js/` | Map interactions, country filter, conditional form fields. |
| `resources/public/style.css` | Styles, following clojure.org's palette. |

Applications and newsletter sign-ups live in atoms (`app.db`) for now, so
they're gone after a restart.

## Deploying

```sh
npm i -g vercel
vercel deploy
```
