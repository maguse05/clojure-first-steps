(ns app.views.meetup
  (:require [app.views.helpers :as helpers]
            [app.views.layout :as layout]
            [app.views.map :as meetup-map]))

(defn- detail [label & content]
  [:div.detail
   [:dt label]
   [:dd content]])

(defn- next-event [{:keys [title url] :as event}]
  (if-not event
    "TBA"
    (list (helpers/event-date event :long)
          (when title
            [:span.detail__extra
             (if url
               [:a {:href url :target "_blank" :rel "noopener"} title " ↗"]
               title)]))))

(defn- venue [{:keys [name address]}]
  (if-not name
    "TBA"
    (list [:strong name]
          (when address [:span.detail__extra address]))))

(defn page [meetup opts]
  (let [event (helpers/upcoming-event meetup)]
    (layout/page
     (assoc opts
            :title (:name meetup)
            :description (:description meetup)
            :leaflet? true
            :scripts ["/js/meetups.js"])
     [:article.section
      [:div.container
       [:a.back {:href "/#meetups"} "← All meetups"]
       [:header.meetup-header
        [:p.meetup-header__place (helpers/place meetup)]
        [:h1 (:name meetup)]
        (helpers/format-badge (:format meetup))]
       [:div.meetup-layout
        [:div
         [:section
          [:h2 "About"]
          [:p.description (:description meetup)]]
         (when-let [links (seq (:links meetup))]
           [:section
            [:h2 "Links"]
            [:ul.links
             (for [{:keys [label url]} links]
               [:li [:a {:href url :target "_blank" :rel "noopener"} label " ↗"]])]])]
        [:aside.card
         [:dl.details
          (detail "Next date" (next-event event))
          (detail "Format" (helpers/format-labels (:format meetup)))
          (detail "Frequency" (:frequency meetup "TBA"))
          (detail "Organizer" (:organizer meetup "TBA"))
          (detail "Country & City/Region" (helpers/place meetup))
          (detail "Venue & address" (venue (or (:venue event) (:venue meetup))))]
         (meetup-map/meetup-map [meetup] :single? true)]]]])))
