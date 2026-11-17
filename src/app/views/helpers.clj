(ns app.views.helpers
  (:require [clojure.string :as str])
  (:import (java.time LocalDate LocalTime ZoneId ZonedDateTime)
           (java.time.format DateTimeFormatter)
           (java.util Locale)))

(def format-labels
  (array-map :in-person "In person"
             :hybrid    "Hybrid"
             :online    "Online"))

(defn format-badge [meetup-format]
  [:span {:class (str "badge badge--" (name meetup-format))}
   (format-labels meetup-format)])

(defn meetup-path [{:keys [slug]}]
  (str "/meetups/" slug))

(defn place [{:keys [city country]}]
  (str/join ", " (remove str/blank? [city country])))

(defn- starts-at ^ZonedDateTime [{:keys [date time time-zone]}]
  (ZonedDateTime/of (LocalDate/parse date)
                    (if time (LocalTime/parse time) LocalTime/MAX)
                    (ZoneId/of (or time-zone "UTC"))))

(defn upcoming-event
  "The meetup's `:next-event`, unless it has already happened.
  Events without a `:time` count as upcoming until the end of that day."
  [{:keys [next-event]}]
  (when next-event
    (let [start (starts-at next-event)]
      (when (.isAfter start (ZonedDateTime/now (.getZone start)))
        (assoc next-event :starts-at start)))))

(defn by-next-event
  "Meetups with an upcoming event first, soonest first; the rest by name."
  [meetups]
  (let [start (fn [m] (some-> (upcoming-event m) ^ZonedDateTime (:starts-at) (.toInstant)))]
    (sort-by (juxt (comp nil? start) start :name) meetups)))

(def ^:private date-formats
  {:short (DateTimeFormatter/ofPattern "EEE d MMM yyyy" Locale/ENGLISH)
   :long  (DateTimeFormatter/ofPattern "EEEE, d MMMM yyyy" Locale/ENGLISH)})

(def ^:private time-format
  (DateTimeFormatter/ofPattern "HH:mm z" Locale/ENGLISH))

(defn event-date [{:keys [^ZonedDateTime starts-at time]} style]
  (cond-> (.format starts-at ^DateTimeFormatter (date-formats style))
    (and time (= :long style)) (str " · " (.format starts-at time-format))))
