(ns app.views
  "Builds the HTML pages with Hiccup: Clojure vectors that turn into HTML.
  [:p {:class \"x\"} \"hi\"]  =>  <p class=\"x\">hi</p>"
  (:require [clojure.string :as str]
            [hiccup2.core :as h])
  (:import (java.time LocalDateTime)
           (java.time.format DateTimeFormatter)
           (java.util Locale)))

(def github-repo "https://github.com/maguse05/clojure-first-steps")

(def community-links
  [{:label       "#events on Clojurians Slack"
    :description "Announce and discover Clojure events worldwide."
    :url         "https://clojurians.slack.com/app_redirect?channel=events"
    :hint        [:span "New to Slack? " [:a {:href "https://clojurians.net"} "Get an invite"]]}
   {:label       "Clojure/Conj on LinkedIn"
    :description "News about the Clojure/Conj conference and the community."
    :url         "https://www.linkedin.com/company/clojure-conj/"}])

;; ---------------------------------------------------------------------------
;; Helpers

(def ^:private date-format
  (DateTimeFormatter/ofPattern "EEEE, d MMMM yyyy · HH:mm" Locale/ENGLISH))

(def ^:private short-date-format
  (DateTimeFormatter/ofPattern "EEE d MMM yyyy" Locale/ENGLISH))

(defn- parse-date [s]
  (when s (LocalDateTime/parse s)))

(defn- format-date [s fmt]
  (if-let [d (parse-date s)]
    (.format d fmt)
    "TBA"))

(defn meetup-url [{:keys [slug]}]
  (str "/meetups/" slug))

(defn- place [{:keys [city country]}]
  (str/join ", " (remove str/blank? [city country])))

(defn- sort-by-next-date
  "Scheduled meetups first (soonest first), then the ones without a date."
  [meetups]
  (sort-by (fn [m] [(nil? (:next-date m)) (:next-date m) (:name m)]) meetups))

;; ---------------------------------------------------------------------------
;; Layout pieces

(def ^:private map-script
  "Draws a Leaflet map in #map with a pin for every element that has data-lat."
  "(function () {
  var el = document.getElementById('map');
  if (!el || !window.L) return;
  var map = L.map(el, { scrollWheelZoom: false });
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors'
  }).addTo(map);
  var points = [];
  document.querySelectorAll('[data-lat]').forEach(function (n) {
    var ll = [parseFloat(n.dataset.lat), parseFloat(n.dataset.lng)];
    points.push(ll);
    var box = document.createElement('div');
    var link = document.createElement(n.dataset.href ? 'a' : 'strong');
    if (n.dataset.href) link.href = n.dataset.href;
    link.textContent = n.dataset.name;
    var where = document.createElement('div');
    where.textContent = n.dataset.place;
    box.appendChild(link); box.appendChild(where);
    L.marker(ll).addTo(map).bindPopup(box);
  });
  function fit() {
    map.invalidateSize();
    if (points.length === 1) map.setView(points[0], 13);
    else if (points.length) map.fitBounds(points, { padding: [40, 40], maxZoom: 6 });
    else map.setView([20, 0], 2);
  }
  fit();
  // Re-fit when the map's box changes size (e.g. phone rotation, window resize).
  if (window.ResizeObserver) new ResizeObserver(fit).observe(el);
})();")

(defn- site-header []
  [:header.site-header
   [:div.container.site-header__inner
    [:a.brand {:href "/"}
     [:span.brand__mark {:aria-hidden "true"} "λ"]
     [:span "Clojure Meetups"]]
    [:nav
     [:a {:href "/#map-section"} "Map"]
     [:a {:href "/#meetups"} "Meetups"]
     [:a.button.button--small {:href "/#add-meetup"} "Add your meetup"]]]])

(defn- notice [kind text]
  [:p {:class (str "notice notice--" (name kind)) :role "status"} text])

(defn- site-footer [{:keys [path flash]}]
  [:footer.site-footer
   [:div.container
    [:h2 "Connect with the community"]
    [:div.footer-grid
     (for [{:keys [label description url hint]} community-links]
       [:div.footer-card
        [:a.footer-card__title {:href url :target "_blank" :rel "noopener"} label " ↗"]
        [:p description]
        (when hint [:p.footer-card__hint hint])])
     [:div.footer-card {:id "newsletter"}
      [:span.footer-card__title "Newsletter"]
      [:p "Get new meetups and events in your inbox. No spam."]
      (case flash
        :subscribed (notice :success "Thanks! You're on the list.")
        :bad-email  (notice :error "That email doesn't look right — please try again.")
        nil)
      [:form.newsletter {:method "post" :action "/subscribe"}
       [:input {:type "hidden" :name "back" :value path}]
       [:label.visually-hidden {:for "newsletter-email"} "Email address"]
       [:input {:id "newsletter-email" :type "email" :name "email" :required true
                :placeholder "you@example.com" :autocomplete "email"}]
       [:button.button {:type "submit"} "Sign up"]]]]
    [:p.site-footer__small
     "Made with Clojure. "
     [:a {:href github-repo} "Source on GitHub"]]]])

(defn- layout
  "Wraps page content in the shared <head>, header and footer."
  [{:keys [title description map?] :as opts} & content]
  (str
   "<!DOCTYPE html>"
   (h/html
    [:html {:lang "en"}
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:title (if title (str title " · Clojure Meetups") "Clojure Meetups")]
      [:meta {:name "description"
              :content (or description "Find a Clojure meetup near you.")}]
      [:link {:rel "stylesheet" :href "/style.css"}]
      (when map?
        [:link {:rel "stylesheet" :href "https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"}])]
     [:body
      (site-header)
      [:main content]
      (site-footer opts)
      (when map?
        (list
         [:script {:src "https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"}]
         [:script (h/raw map-script)]))]])))

;; ---------------------------------------------------------------------------
;; Homepage

(defn- meetup-card [m]
  [:li.meetup-card
   {:data-lat (:lat m) :data-lng (:lng m) :data-name (:name m)
    :data-place (place m) :data-href (meetup-url m)}
   [:a.meetup-card__link {:href (meetup-url m)}
    [:span.meetup-card__place (place m)]
    [:span.meetup-card__name (:name m)]
    [:span.meetup-card__meta
     [:span (str "Next: " (format-date (:next-date m) short-date-format))]
     [:span (:frequency m)]]]])

(defn- field [{:keys [id label type required? placeholder]}]
  [:div.field
   [:label {:for id} label (when-not required? [:span.optional " (optional)"])]
   (if (= type "textarea")
     [:textarea {:id id :name id :rows 3 :placeholder placeholder}]
     [:input {:id id :name id :type (or type "text") :required required?
              :placeholder placeholder}])])

(defn- add-meetup-section [flash]
  [:section.section.add-meetup {:id "add-meetup"}
   [:div.container.add-meetup__inner
    [:div
     [:h2 "Add your meetup to the website"]
     [:p "Running a Clojure meetup? Tell us about it and we'll add it to the map."]
     [:p.muted "Comfortable with Git? You can also open a pull request that adds your meetup to "
      [:a {:href (str github-repo "/blob/main/resources/meetups.edn")} [:code "resources/meetups.edn"]] "."]]
    [:form.card.add-meetup__form {:method "post" :action "/meetups/submit"}
     (case flash
       :submitted   (notice :success "Thanks! We'll review your meetup and add it soon.")
       :bad-meetup  (notice :error "Please fill in the meetup name, city, country and a valid contact email.")
       nil)
     [:div.form-grid
      (field {:id "name" :label "Meetup name" :required? true :placeholder "Clojure Lisbon"})
      (field {:id "organizer" :label "Organizer" :required? true :placeholder "Your name"})
      (field {:id "city" :label "City / Region" :required? true :placeholder "Lisbon"})
      (field {:id "country" :label "Country" :required? true :placeholder "Portugal"})
      (field {:id "frequency" :label "Frequency" :placeholder "Monthly"})
      (field {:id "contact" :label "Contact email" :type "email" :required? true :placeholder "you@example.com"})]
     (field {:id "link" :label "Link" :type "url" :placeholder "https://www.meetup.com/…"})
     (field {:id "description" :label "Description" :type "textarea"
             :placeholder "What happens at your meetup?"})
     [:button.button {:type "submit"} "Submit meetup"]]]])

(defn home-page [meetups {:keys [flash] :as opts}]
  (layout
   (assoc opts :map? true)
   [:section.hero
    [:div.container
     [:h1 "Find a Clojure meetup near you"]
     [:p.hero__lead
      (str (count meetups) " groups in "
           (count (distinct (map :country meetups)))
           " countries bringing Clojurians together — in person and online.")]]]

   [:section.section {:id "map-section"}
    [:div.container
     [:h2.visually-hidden "Meetup map"]
     [:div#map.map.map--large {:role "region" :aria-label "Map of Clojure meetups"}]]]

   [:section.section {:id "meetups"}
    [:div.container
     [:h2 "All meetups"]
     [:ul.meetup-grid
      (map meetup-card (sort-by-next-date meetups))]]]

   (add-meetup-section flash)))

;; ---------------------------------------------------------------------------
;; Meetup page

(defn- detail [label & value]
  [:div.detail
   [:dt label]
   [:dd value]])

(defn meetup-page [m opts]
  (layout
   (assoc opts :title (:name m) :description (:description m) :map? true)
   [:article.section
    [:div.container
     [:a.back {:href "/#meetups"} "← All meetups"]
     [:header.meetup-header
      [:p.meetup-header__place (place m)]
      [:h1 (:name m)]]
     [:div.meetup-layout
      [:div
       [:section
        [:h2 "About"]
        [:p.description (:description m)]]
       (when (seq (:links m))
         [:section
          [:h2 "Links"]
          [:ul.links
           (for [{:keys [label url]} (:links m)]
             [:li [:a {:href url :target "_blank" :rel "noopener"} label " ↗"]])]])]
      [:aside.card
       [:dl.details
        (detail "Next date" (format-date (:next-date m) date-format))
        (detail "Frequency" (or (:frequency m) "—"))
        (detail "Organizer" (or (:organizer m) "—"))
        (detail "Country & City/Region" (place m))
        (detail "Venue & address"
                (if-let [{venue :name address :address} (:venue m)]
                  (list [:strong venue] [:br] address)
                  "TBA"))]
       [:div#map.map.map--small
        {:role "region" :aria-label (str "Map showing " (:name m))}]
       [:span {:hidden true :data-lat (:lat m) :data-lng (:lng m)
               :data-name (:name m) :data-place (place m)}]]]]]))

;; ---------------------------------------------------------------------------
;; Not found

(defn not-found-page [opts]
  (layout
   (assoc opts :title "Not found")
   [:section.section.hero
    [:div.container
     [:h1 "Page not found"]
     [:p [:a {:href "/"} "Back to all meetups"]]]]))
