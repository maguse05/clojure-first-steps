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

Tests:

```sh
clojure -X:test
```

## Configuration

| Variable | |
| --- | --- |
| `MAILCHIMP_API_KEY` | Mailchimp API key, e.g. `abc123-us21`. |
| `MAILCHIMP_AUDIENCE_ID` | ID of the audience newsletter sign-ups go to. |
| `APPLICATIONS_URL` | URL of the Apps Script web app that adds applications to the spreadsheet. |
| `APPLICATIONS_SECRET` | Shared with the web app, logged by its `setUp`. |

Without them, sign-ups and applications are only kept in memory. On Vercel
they go under Project → Settings → Environment Variables.

## Meetups

The applications spreadsheet decides which meetups are listed: a meetup shows
up once its "Sponsorship Setup?" cell says Yes. Every hour,
[`apps-script/sync-meetups.gs`](apps-script/sync-meetups.gs) commits the
approved ones to `resources/meetups-sheet.json`, which redeploys the site. It
only exports public fields, plus:

- the URLs in "Generic Meetup Link" (`TBC` or blank means no link yet),
- the next meetup date from the organizer's latest Post-Event Report,
- past meetups: date, title, recording and blog post from each report, plus
  the photos and links in the meetup's Drive folder,
- coordinates for the map, from the city and country.

Names come from "Final Meetup name"; while it's `TBC` or blank, the meetup
shows up as "<city> Meetup". The About text comes from "Website description",
written by the organizers: the site's form asks for it, and for older
applications the team pastes what organizers send. A blank line starts a new
paragraph; without a description the page has no About section. `resources/meetups.edn` adds what a form answer
can't give: a tidy city, coordinates, a description, venue and event details.
Entries are matched to the sheet on `:applied-at`, the application's
timestamp.

## Past meetups

The past meetups folder in Drive has a folder per meetup, named like the
meetup, and in it a folder per date (`Nov 24, 2026` or `2026-11-24`) with the
photos. A Google Doc in a date's folder adds its links, one per line, like
`Slides: https://...`; YouTube links are embedded. The script resizes new
photos to 1600px and commits them to `resources/public/photos`, so the Drive
folder can stay private. Folder names that match no approved meetup show up
as warnings in the script's executions.

The same script takes the applications sent from the site's form and adds
them to the responses sheet, so the Google Form can stay closed, and emails
the team about each one.

To set it up, open the spreadsheet, Extensions → Apps Script, paste the file,
add a `GITHUB_TOKEN` script property (fine-grained token, Contents read and
write on this repository only) and run `setUp` once. Then deploy it as a web
app, executed as you and accessible by anyone, and set `APPLICATIONS_URL` and
`APPLICATIONS_SECRET` on Vercel.

## Cities

The application form suggests the cities of the chosen country, from
`resources/public/cities/<country code>.json`. They're built from
[GeoNames](https://www.geonames.org) (CC BY 4.0), every place of 1000 people
or more; `dev/cities.clj` says how to rebuild them.

## Layout

| Path | |
| --- | --- |
| `resources/meetups-sheet.json` | Approved meetups, written by the Apps Script. Don't edit by hand. |
| `resources/meetups.edn` | Curated meetup details. Re-read on every request, no restart needed. |
| `resources/public/photos/` | Past meetup photos, written by the Apps Script. |
| `resources/public/cities/` | City suggestions per country, from GeoNames. |
| `src/app/meetups.clj` | Merges the two into what the site lists. |
| `src/app/routes.clj` | Routes and handlers. |
| `src/app/application.clj` | Application form fields and validation, as data. |
| `src/app/countries.clj` | Country names and matching for the country filter. |
| `src/app/views/` | Hiccup views: layout, home, meetup page, map, form. |
| `resources/public/js/` | Map interactions, country filter, conditional form fields. |
| `resources/public/style.css` | Styles, following clojure.org's palette. |

Newsletter sign-ups go to Mailchimp (`app.mailchimp`) as pending, tagged
"Clojure Meetups website". Applications go to the spreadsheet (`app.sheet`),
and applicants to Mailchimp as transactional contacts, tagged "Meetup
applicant".

## Deploying

```sh
npm i -g vercel
vercel deploy
```
