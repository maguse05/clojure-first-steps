(ns app.views.home
  (:require [app.countries :as countries]
            [app.views.form :as form]
            [app.views.helpers :as helpers]
            [app.views.layout :as layout]
            [app.views.map :as meetup-map]))

(defn- meetup-card [meetup hidden?]
  (let [event (helpers/upcoming-event meetup)]
    [:li.meetup-card {:data-slug (:slug meetup) :hidden hidden?}
     [:a.meetup-card__link {:href (helpers/meetup-path meetup)}
      [:span.meetup-card__top
       [:span.meetup-card__place (helpers/place meetup)]
       (helpers/format-badge (:format meetup))]
      [:span.meetup-card__name (:name meetup)]
      [:span.meetup-card__meta
       [:span "Next: " (if event (helpers/event-date event :short) "TBA")]
       [:span (:frequency meetup)]]]]))

(defn- meetup-list [meetups query]
  (let [shown?  #(countries/matches? query (:country %))
        n-shown (count (filter shown? meetups))
        country (countries/canonical query)
        none?   (zero? n-shown)]
    [:section.section {:id "meetups"}
     [:div.container
      [:div.meetups-header
       [:h2 "All meetups"]
       [:form.country-filter {:action "/" :method "get" :role "search"}
        [:label.visually-hidden {:for "country-filter"} "Filter by country"]
        [:input#country-filter {:type "search" :name "country" :value query :list "countries"
                                :placeholder "Filter by country" :autocomplete "off"}]
        [:button.button.button--small {:type "submit"} "Filter"]]]
      [:p.meetups-count {:aria-live "polite"}
       [:span.meetups-count__shown n-shown] " of " (count meetups) " meetups"]
      [:ul.meetup-grid
       (for [m (helpers/by-next-event meetups)]
         (meetup-card m (not (shown? m))))]
      [:div.empty-state {:data-empty "country" :hidden (not (and none? country))}
       [:p.empty-state__title
        "There's no Clojure meetup in " [:span.empty-state__country country] " yet."]
       [:p "Be the one who brings your local Clojure community together. "
        "Tell us about the meetup you'd like to start and we'll help you get it off the ground."]
       [:a.button {:href "#apply" :data-apply-country (str country)} "Apply with your meetup proposal"]]
      [:div.empty-state {:data-empty "none" :hidden (not (and none? (not country)))}
       [:p.empty-state__title
        "No meetups match “" [:span.empty-state__query query] "”."]
       [:p "Check the spelling, or pick a country from the list."]]]]))

(defn- apply-section [{:keys [values errors]} flash]
  [:section.section.apply {:id "apply"}
   [:div.container
    [:div.apply__intro
     [:h2 "Add your meetup to the website"]
     [:p.apply__lead
      "Run a Clojure meetup, or want to start one? Apply to the Clojure Meetup Pilot Program. "
      "Selected meetups get monthly support for things like venue, food and promotion, "
      "and a pin on this map."]]
    (form/application-form {:values     values
                            :errors     errors
                            :submitted? (= :applied flash)})]])

(defn page [{:keys [meetups query application flash] :as opts}]
  (layout/page
   (assoc opts :leaflet? true :scripts ["/js/meetups.js" "/js/application.js"])
   [:section.hero
    [:div.container
     [:h1 "Find a " [:span.highlight "Clojure meetup"] " near you"]
     [:p.hero__lead
      (count meetups) " meetups in " (count (distinct (map :country meetups)))
      " countries bringing Clojurians together, in person and online."]]]
   [:section.section.map-section
    [:div.container
     [:h2.visually-hidden "Meetup map"]
     (meetup-map/meetup-map meetups)]]
   (meetup-list meetups query)
   (apply-section application flash)
   [:datalist#countries
    (for [c countries/names]
      [:option {:value c}])]))
