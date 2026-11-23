(ns app.views.map
  (:require [app.countries :as countries]
            [app.views.helpers :as helpers]
            [clojure.data.json :as json]
            [hiccup2.core :as h]))

(defn- marker [{:keys [slug name country frequency lat lng] :as meetup}]
  {:slug      slug
   :name      name
   :country   country
   :place     (helpers/place meetup)
   :frequency frequency
   :format    {:id    (clojure.core/name (:format meetup))
               :label (helpers/format-labels (:format meetup))}
   :lat       lat
   :lng       lng
   :url       (helpers/meetup-path meetup)})

(defn- legend []
  [:ul.map-legend {:aria-label "Meetup formats"}
   (for [[k label] helpers/format-labels]
     [:li [:span {:class (str "map-legend__dot map-legend__dot--" (name k))}] label])])

(defn meetup-map
  "The Leaflet map container plus the JSON that js/meetups.js builds pins from."
  [meetups & {:keys [single?]}]
  (list
   [:div.map-wrap
    [:div#map.map {:class      (if single? "map--small" "map--large")
                   :data-mode  (when single? "single")
                   :role       "region"
                   :aria-label (if single? "Meetup location" "Map of Clojure meetups")}]
    (when-not single? (legend))]
   [:script#meetup-data {:type "application/json"}
    (h/raw (json/write-str {:meetups (map marker (filter :lat meetups))
                            :aliases countries/aliases}))]))
